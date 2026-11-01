package com.alert_service.rule;

import static com.alert_service.testing.Bars.bar;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import com.alert_service.marketdata.DataType;
import com.alert_service.marketdata.OhlcvBar;
import com.alert_service.marketdata.OhlcvJsonDecoder;
import com.alert_service.marketdata.Topic;
import com.alert_service.marketdata.zmq.ProviderManager;
import com.alert_service.rule.RuleRegistry.Attachment;
import com.alert_service.rule.aggregate.sequencerangesweep.SequenceRangeSweepFactory;
import com.alert_service.rule.basic.rangebreak.RangeBreakWatchFactory;
import com.alert_service.rule.mock.NeverFiringRuleFactory;
import com.alert_service.testing.FakeDataProviderFactory;
import com.alert_service.timegrid.TimeGrid;

import tools.jackson.databind.json.JsonMapper;

class RuleRegistryTest {

	private static final List<String> FOUR = List.of("ES", "NQ", "YM", "RTY");

	private final FakeDataProviderFactory providers = new FakeDataProviderFactory();
	private final RuleRegistry registry = new RuleRegistry(List.of(new RangeBreakWatchFactory(TimeGrid.cme())),
			List.of(new SequenceRangeSweepFactory(), new NeverFiringRuleFactory()),
			new ProviderManager(providers, List.of(new OhlcvJsonDecoder(JsonMapper.builder().build()))));

	private static Topic topic(String symbol) {
		return new Topic(DataType.OHLCV, symbol);
	}

	private Attachment subscribe(String type, List<String> symbols, String subscriberId) {
		return registry.subscribe(registry.keyFor(type, symbols), subscriberId, result -> {
		});
	}

	@Test
	void keyIsNormalized() {
		assertThat(registry.keyFor("SEQUENCE_RANGE_SWEEP", List.of("YM", "ES", "NQ", "RTY", "ES")))
			.hasToString("SEQUENCE_RANGE_SWEEP[ES,NQ,RTY,YM]");
	}

	@Test
	void rejectsInvalidRequests() {
		assertThatIllegalArgumentException().isThrownBy(() -> registry.keyFor("NOPE", FOUR))
			.withMessageContaining("Unknown rule");
		assertThatIllegalArgumentException().isThrownBy(() -> registry.keyFor("SEQUENCE_RANGE_SWEEP", List.of("ES")))
			.withMessageContaining("at least 2");
		assertThatIllegalArgumentException()
			.isThrownBy(() -> registry.keyFor("SEQUENCE_RANGE_SWEEP", List.of("ES", "n q")));
		assertThatIllegalArgumentException().isThrownBy(() -> registry.keyFor("SEQUENCE_RANGE_SWEEP", null));
	}

	@Test
	void rejectsAggregateWiredToUnknownBasicRule() {
		assertThatIllegalStateException()
			.isThrownBy(() -> new RuleRegistry(List.of(), List.of(new SequenceRangeSweepFactory()),
					new ProviderManager(providers, List.of())))
			.withMessageContaining("unknown basic rule");
	}

	@Test
	void createsOneBasicRuleAndProviderPerSymbol() {
		subscribe("SEQUENCE_RANGE_SWEEP", FOUR, "user");

		assertThat(providers.created()).extracting(p -> p.topic().symbol())
			.containsExactlyInAnyOrder("ES", "NQ", "YM", "RTY");
		assertThat(registry.activeKeys()).extracting(RuleKey::toString)
			.containsExactlyInAnyOrder("SEQUENCE_RANGE_SWEEP[ES,NQ,RTY,YM]", "RANGE_BREAK_WATCH[ES]",
					"RANGE_BREAK_WATCH[NQ]", "RANGE_BREAK_WATCH[YM]", "RANGE_BREAK_WATCH[RTY]");
	}

	@Test
	void sameKeySharesTheInstance() {
		Attachment first = subscribe("SEQUENCE_RANGE_SWEEP", FOUR, "a");
		Attachment second = subscribe("SEQUENCE_RANGE_SWEEP", List.of("RTY", "YM", "NQ", "ES"), "b");

		assertThat(second.rule()).isSameAs(first.rule());
		assertThat(providers.created()).hasSize(4);
	}

	@Test
	void aggregatesShareBasicRulesAndProviders() {
		subscribe("SEQUENCE_RANGE_SWEEP", FOUR, "a");
		subscribe("NEVER_FIRING", List.of("ES"), "b");

		assertThat(providers.created()).hasSize(4);
		assertThat(registry.activeKeys()).hasSize(6);
	}

	@Test
	void lastListenerLeavingCascadesDownToProviders() {
		Attachment sweep = subscribe("SEQUENCE_RANGE_SWEEP", FOUR, "a");
		Attachment mock = subscribe("NEVER_FIRING", List.of("ES"), "b");

		sweep.subscription().close();
		assertThat(registry.activeKeys()).extracting(RuleKey::toString)
			.containsExactlyInAnyOrder("NEVER_FIRING[ES]", "RANGE_BREAK_WATCH[ES]");
		assertThat(providers.isOpen(topic("ES"))).isTrue();
		assertThat(providers.isOpen(topic("NQ"))).isFalse();

		mock.subscription().close();
		assertThat(registry.activeKeys()).isEmpty();
		assertThat(providers.created()).allSatisfy(p -> assertThat(p.isClosed()).isTrue());
	}

	@Test
	void retiredInstanceIsRecreatedOnNextSubscribe() {
		Attachment first = subscribe("SEQUENCE_RANGE_SWEEP", FOUR, "a");
		first.subscription().close();

		Attachment second = subscribe("SEQUENCE_RANGE_SWEEP", FOUR, "a");

		assertThat(second.rule()).isNotSameAs(first.rule());
		assertThat(providers.created()).hasSize(8);
	}

	@Test
	void barsFlowFromProvidersToAggregateResults() throws InterruptedException {
		BlockingQueue<RuleResult> results = new LinkedBlockingQueue<>();
		registry.subscribe(registry.keyFor("SEQUENCE_RANGE_SWEEP", FOUR), "user", results::add);

		String[][] ranges = { { "ES", "6032.00", "6026.00" }, { "NQ", "21120.00", "21090.00" },
				{ "YM", "44240.00", "44180.00" }, { "RTY", "2185.00", "2178.00" } };
		for (String[] r : ranges) {
			push(r[0], bar(r[0], "2026-10-12T13:00:00Z", r[1], r[2]));
			push(r[0], bar(r[0], "2026-10-12T13:45:00Z", r[1], r[2]));
		}
		push("ES", bar("ES", "2026-10-12T14:12:40Z", "6028.00", "6027.00"));
		push("NQ", bar("NQ", "2026-10-12T14:12:40Z", "21110.00", "21108.00"));
		push("RTY", bar("RTY", "2026-10-12T14:12:40Z", "2179.50", "2179.20"));
		push("YM", bar("YM", "2026-10-12T14:12:40Z", "44175.00", "44168.00"));

		RuleResult report = results.poll(5, TimeUnit.SECONDS);
		RuleResult first = results.poll(5, TimeUnit.SECONDS);
		RuleResult second = results.poll(5, TimeUnit.SECONDS);
		RuleResult third = results.poll(5, TimeUnit.SECONDS);
		assertThat(report).isNotNull();
		assertThat(report.kind()).isEqualTo(RuleResult.Kind.REPORT);
		List<RuleResult> checked = List.of(first, second, third);
		assertThat(checked).extracting(r -> r.scope().getFirst()).containsExactlyInAnyOrder("ES", "NQ", "RTY");
		assertThat(checked).filteredOn(r -> "RANGE_SWEEP_EVENT".equals(r.code()))
			.extracting(r -> r.scope().getFirst())
			.containsExactlyInAnyOrder("ES", "RTY");
		assertThat(checked).filteredOn(r -> "RANGE_SWEEP_NO_EVENT".equals(r.code()))
			.extracting(r -> r.scope().getFirst())
			.containsExactly("NQ");
		assertThat(results.poll(200, TimeUnit.MILLISECONDS)).isNull();
	}

	private void push(String symbol, OhlcvBar bar) {
		providers.<OhlcvBar>open(topic(symbol)).push(bar);
	}
}

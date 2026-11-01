package com.alert_service.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.alert_service.marketdata.DataType;
import com.alert_service.marketdata.OhlcvJsonDecoder;
import com.alert_service.marketdata.Topic;
import com.alert_service.marketdata.zmq.ProviderManager;
import com.alert_service.rule.RuleRegistry;
import com.alert_service.rule.aggregate.sequencerangesweep.SequenceRangeSweepFactory;
import com.alert_service.rule.basic.rangebreak.RangeBreakWatchFactory;
import com.alert_service.rule.mock.NeverFiringRuleFactory;
import com.alert_service.run.SessionSubscriptions.Ref;
import com.alert_service.signal.SignalService;
import com.alert_service.testing.FakeDataProviderFactory;
import com.alert_service.timegrid.TimeGrid;

import tools.jackson.databind.json.JsonMapper;

class RunServiceTest {

	private static final String SWEEP = "SEQUENCE_RANGE_SWEEP[ES,NQ,RTY,YM]";

	private final FakeDataProviderFactory providers = new FakeDataProviderFactory();
	private final RuleRegistry registry = new RuleRegistry(List.of(new RangeBreakWatchFactory(TimeGrid.cme())),
			List.of(new SequenceRangeSweepFactory(), new NeverFiringRuleFactory()),
			new ProviderManager(providers, List.of(new OhlcvJsonDecoder(JsonMapper.builder().build()))));
	private final SignalService signals = new SignalService(registry, (key, signal) -> {
	}, TimeGrid.cme());
	private final RunService runs = new RunService(registry, signals);

	private static Ref ref(String session, String subscription) {
		return new Ref(session, subscription);
	}

	@Test
	void subscribingStartsTheRule() {
		runs.subscribe(ref("A", "sub-0"), SWEEP);

		assertThat(runs.runs()).isEqualTo(Map.of(SWEEP, Set.of("A")));
		assertThat(providers.created()).hasSize(4);
		assertThat(signals.history(SWEEP)).isPresent();
	}

	@Test
	void subscriptionsShareOneRuleInstance() {
		runs.subscribe(ref("A", "sub-0"), SWEEP);
		runs.subscribe(ref("B", "sub-3"), SWEEP);
		runs.subscribe(ref("B", "sub-3"), SWEEP);

		assertThat(runs.runs()).isEqualTo(Map.of(SWEEP, Set.of("A", "B")));
		assertThat(providers.created()).hasSize(4);
	}

	@Test
	void lastUnsubscribeTearsTheRuleDown() {
		runs.subscribe(ref("A", "sub-0"), SWEEP);
		runs.subscribe(ref("B", "sub-0"), SWEEP);

		assertThat(runs.unsubscribe(ref("A", "sub-0"))).isTrue();
		assertThat(registry.activeKeys()).isNotEmpty();
		assertThat(runs.unsubscribe(ref("B", "sub-0"))).isTrue();

		assertThat(runs.runs()).isEmpty();
		assertThat(registry.activeKeys()).isEmpty();
		assertThat(signals.history(SWEEP)).isEmpty();
		assertThat(providers.created()).allSatisfy(p -> assertThat(p.isClosed()).isTrue());
	}

	@Test
	void unsubscribingSomethingNotRunningIsHarmless() {
		runs.subscribe(ref("A", "sub-0"), SWEEP);

		assertThat(runs.unsubscribe(ref("A", "sub-9"))).isFalse();
		assertThat(runs.unsubscribe(ref("Z", "sub-0"))).isFalse();
		assertThat(runs.runs()).containsKey(SWEEP);
	}

	@Test
	void unsubscribeAllEndsEveryRunOfTheSession() {
		runs.subscribe(ref("A", "sub-0"), SWEEP);
		runs.subscribe(ref("A", "sub-1"), "NEVER_FIRING[ES]");
		runs.subscribe(ref("B", "sub-0"), "NEVER_FIRING[ES]");

		runs.unsubscribeAll("A");

		assertThat(runs.runs()).isEqualTo(Map.of("NEVER_FIRING[ES]", Set.of("B")));
		assertThat(providers.isOpen(new Topic(DataType.OHLCV, "ES"))).isTrue();
		assertThat(registry.activeKeys()).hasSize(2);
	}

	@Test
	void invalidKeysStartNothing() {
		assertThatIllegalArgumentException().isThrownBy(() -> runs.subscribe(ref("A", "1"), "SEQUENCE_RANGE_SWEEP"))
			.withMessageContaining("Malformed");
		assertThatIllegalArgumentException().isThrownBy(() -> runs.subscribe(ref("A", "1"), "NOPE[ES]"))
			.withMessageContaining("Unknown rule");
		assertThatIllegalArgumentException()
			.isThrownBy(() -> runs.subscribe(ref("A", "1"), "SEQUENCE_RANGE_SWEEP[ES]"))
			.withMessageContaining("at least 2");
		assertThatIllegalArgumentException()
			.isThrownBy(() -> runs.subscribe(ref("A", "1"), "SEQUENCE_RANGE_SWEEP[es,nq]"));
		assertThatIllegalArgumentException()
			.isThrownBy(() -> runs.subscribe(ref("A", "1"), "SEQUENCE_RANGE_SWEEP[YM,ES,NQ,RTY]"))
			.withMessageContaining("canonical")
			.withMessageContaining(SWEEP);

		assertThat(runs.runs()).isEmpty();
		assertThat(providers.created()).isEmpty();
	}
}

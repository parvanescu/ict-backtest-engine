package com.alert_service.demo;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import com.alert_service.marketdata.DataType;
import com.alert_service.marketdata.OhlcvBar;
import com.alert_service.marketdata.OhlcvJsonDecoder;
import com.alert_service.marketdata.Topic;
import com.alert_service.marketdata.zmq.ProviderManager;
import com.alert_service.rule.RuleKey;
import com.alert_service.rule.RuleRegistry;
import com.alert_service.rule.aggregate.sequencerangesweep.SequenceRangeSweepFactory;
import com.alert_service.rule.basic.rangebreak.RangeBreakWatchFactory;
import com.alert_service.signal.Signal;
import com.alert_service.signal.SignalService;
import com.alert_service.testing.FakeDataProviderFactory;
import com.alert_service.timegrid.TimeGrid;

import tools.jackson.databind.json.JsonMapper;

/** Every scenario, played into the real rule, produces exactly the range sweep events it promises. */
class ScenariosTest {

	/** Expected range sweep events per scenario id: "SYMBOL@HH:mm:ss" (New York time of the trigger second). */
	private static final Map<String, Set<String>> EXPECTED = Map.of(
			"low-sweep", Set.of("ES@10:12:40", "RTY@10:12:40"),
			"high-sweep", Set.of("YM@10:15:20", "RTY@10:15:20"),
			"no-event", Set.of(),
			"repeat-triggers", Set.of("ES@10:12:40", "RTY@10:12:40", "YM@10:20:15"));

	static Stream<Scenario> scenarios() {
		return Scenarios.ALL.stream();
	}

	@Test
	void fourScenariosWithUniqueIdsAndExpectations() {
		assertThat(Scenarios.ALL).extracting(Scenario::id).containsExactlyInAnyOrderElementsOf(EXPECTED.keySet());
		assertThat(Scenarios.ALL).allSatisfy(s -> assertThat(s.expected()).isNotEmpty());
		assertThat(Scenarios.byId("nope")).isEmpty();
		assertThat(Scenarios.byId("low-sweep")).isPresent();
	}

	@ParameterizedTest
	@MethodSource("scenarios")
	void barsStayInsideTheWindowAndFillEverySecond(Scenario scenario) throws Exception {
		Map<String, List<Long>> times = new HashMap<>();
		ScenarioPlayer.play(scenario, bar -> times.computeIfAbsent(bar.symbol(), k -> new ArrayList<>()).add(bar.startTime()));

		long first = ScenarioPlayer.windowStart().getEpochSecond() * 1_000_000_000L;
		assertThat(times.keySet()).containsExactlyInAnyOrder("ES", "NQ", "YM", "RTY");
		times.values().forEach(list -> {
			assertThat(list).hasSize(5_400);
			assertThat(list.getFirst()).isEqualTo(first);
			assertThat(list.getLast()).isEqualTo(first + 5_399L * 1_000_000_000L);
		});
		assertThat(ScenarioPlayer.windowEnd()).isEqualTo(ScenarioPlayer.windowStart().plusSeconds(5_400));
	}

	@ParameterizedTest
	@MethodSource("scenarios")
	void theRuleReportsExactlyTheExpectedEvents(Scenario scenario) throws Exception {
		FakeDataProviderFactory providers = new FakeDataProviderFactory();
		RuleRegistry registry = new RuleRegistry(List.of(new RangeBreakWatchFactory(TimeGrid.cme())),
				List.of(new SequenceRangeSweepFactory()),
				new ProviderManager(providers, List.of(new OhlcvJsonDecoder(JsonMapper.builder().build()))));
		List<Signal> signals = new CopyOnWriteArrayList<>();
		SignalService service = new SignalService(registry, (ruleKey, signal) -> signals.add(signal),
				TimeGrid.cme());
		RuleKey key = registry.keyFor("SEQUENCE_RANGE_SWEEP", List.of("ES", "NQ", "YM", "RTY"));
		service.attach(key);

		ScenarioPlayer.play(scenario,
				bar -> providers.<OhlcvBar>open(new Topic(DataType.OHLCV, bar.symbol())).push(bar));
		int seen;
		do {
			seen = signals.size();
			Thread.sleep(300);
		}
		while (signals.size() != seen);

		Set<String> events = new TreeSet<>();
		for (Signal signal : signals) {
			if ("RANGE_SWEEP_EVENT".equals(signal.code())) {
				LocalTime time = Instant.parse(signal.eventTime()).atZone(TimeGrid.NEW_YORK).toLocalTime();
				events.add(signal.scope().getFirst() + "@" + time);
			}
		}
		assertThat(events).containsExactlyInAnyOrderElementsOf(EXPECTED.get(scenario.id()));
	}
}

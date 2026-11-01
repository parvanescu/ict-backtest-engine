package com.alert_service.signal;

import static com.alert_service.testing.Bars.bar;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
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
import com.alert_service.rule.DayStatus;
import com.alert_service.rule.RuleKey;
import com.alert_service.rule.RuleRegistry;
import com.alert_service.rule.aggregate.sequencerangesweep.SequenceRangeSweepFactory;
import com.alert_service.rule.basic.rangebreak.RangeBreakWatchFactory;
import com.alert_service.signal.SignalService.SignalHistory;
import com.alert_service.signal.SignalService.SignalStream;
import com.alert_service.testing.FakeDataProviderFactory;
import com.alert_service.timegrid.TimeGrid;

import tools.jackson.databind.json.JsonMapper;

class SignalServiceTest {

	private record Published(String ruleKey, Signal signal) {
	}

	private static final List<String> FOUR = List.of("ES", "NQ", "YM", "RTY");

	private final FakeDataProviderFactory providers = new FakeDataProviderFactory();
	private final RuleRegistry registry = new RuleRegistry(List.of(new RangeBreakWatchFactory(TimeGrid.cme())),
			List.of(new SequenceRangeSweepFactory()),
			new ProviderManager(providers, List.of(new OhlcvJsonDecoder(JsonMapper.builder().build()))));
	private final BlockingQueue<Published> published = new LinkedBlockingQueue<>();
	private final SignalService service = new SignalService(registry,
			(ruleKey, signal) -> published.add(new Published(ruleKey, signal)), TimeGrid.cme());
	private final RuleKey key = registry.keyFor("SEQUENCE_RANGE_SWEEP", FOUR);

	private void push(String symbol, OhlcvBar bar) {
		providers.<OhlcvBar>open(new Topic(DataType.OHLCV, symbol)).push(bar);
	}

	private void playWorkedExample(String day) {
		String[][] ranges = { { "ES", "6032.00", "6026.00" }, { "NQ", "21120.00", "21090.00" },
				{ "YM", "44240.00", "44180.00" }, { "RTY", "2185.00", "2178.00" } };
		for (String[] r : ranges) {
			push(r[0], bar(r[0], day + "T13:00:00Z", r[1], r[2]));
			push(r[0], bar(r[0], day + "T13:45:00Z", r[1], r[2]));
		}
		push("ES", bar("ES", day + "T14:12:40Z", "6028.00", "6027.00"));
		push("NQ", bar("NQ", day + "T14:12:40Z", "21110.00", "21108.00"));
		push("RTY", bar("RTY", day + "T14:12:40Z", "2179.50", "2179.20"));
		push("YM", bar("YM", day + "T14:12:40Z", "44175.00", "44168.00"));
	}

	private List<Signal> take(int count) throws InterruptedException {
		List<Signal> signals = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			Published p = published.poll(5, TimeUnit.SECONDS);
			assertThat(p).as("signal %d", i + 1).isNotNull();
			assertThat(p.ruleKey()).isEqualTo(key.toString());
			signals.add(p.signal());
		}
		return signals;
	}

	@Test
	void attachIsIdempotentPerKey() {
		SignalStream first = service.attach(key);

		assertThat(service.attach(key)).isSameAs(first);
		assertThat(providers.created()).hasSize(4);
	}

	@Test
	void resetClearsHistoryAndJudgesTheDayAgain() throws InterruptedException {
		service.attach(key);
		playWorkedExample("2026-10-12");
		assertThat(take(3)).extracting(Signal::id).containsExactly(1L, 2L, 3L);

		service.reset();

		SignalHistory history = service.history(key.toString()).orElseThrow();
		assertThat(history.signals()).isEmpty();
		assertThat(history.dayStatus()).isEqualTo(DayStatus.UNKNOWN);

		playWorkedExample("2026-10-12");
		assertThat(take(3)).extracting(Signal::id).containsExactly(4L, 5L, 6L);
	}

	@Test
	void numbersSignalsAndKeepsTodaysHistory() throws InterruptedException {
		service.attach(key);
		playWorkedExample("2026-10-12");

		List<Signal> live = take(3);
		assertThat(live).extracting(Signal::id).containsExactly(1L, 2L, 3L);
		assertThat(live.getFirst().kind()).isEqualTo("REPORT");

		SignalHistory history = service.history(key.toString()).orElseThrow();
		assertThat(history.dayStatus()).isEqualTo(DayStatus.VALID);
		assertThat(history.tradingDay()).isEqualTo(LocalDate.of(2026, 10, 12));
		assertThat(history.signals()).isEqualTo(live);
	}

	@Test
	void historyStartsOverOnANewTradingDay() throws InterruptedException {
		service.attach(key);
		playWorkedExample("2026-10-12");
		take(3);
		playWorkedExample("2026-10-13");
		List<Signal> nextDay = take(3);

		SignalHistory history = service.history(key.toString()).orElseThrow();
		assertThat(history.tradingDay()).isEqualTo(LocalDate.of(2026, 10, 13));
		assertThat(history.signals()).isEqualTo(nextDay);
		assertThat(nextDay).extracting(Signal::id).containsExactly(4L, 5L, 6L);
	}

	@Test
	void closingTheStreamReleasesTheRuleAndItsHistory() {
		SignalStream stream = service.attach(key);

		stream.close();

		assertThat(service.history(key.toString())).isEmpty();
		assertThat(registry.activeKeys()).isEmpty();
		assertThat(service.attach(key)).isNotSameAs(stream);
	}

	@Test
	void errorSignalCarriesOnlyTheMessage() {
		Signal error = Signal.error("NOPE[ES]", Signal.INVALID_SUBSCRIPTION, "Unknown rule NOPE");

		assertThat(error.kind()).isEqualTo("ERROR");
		assertThat(error.id()).isNull();
		assertThat(error.eventTime()).isNull();
		assertThat(error.message()).isEqualTo("Unknown rule NOPE");
	}
}

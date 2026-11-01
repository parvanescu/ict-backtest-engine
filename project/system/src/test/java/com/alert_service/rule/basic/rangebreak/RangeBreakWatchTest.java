package com.alert_service.rule.basic.rangebreak;

import static com.alert_service.testing.Bars.bar;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.alert_service.rule.RuleKey;
import com.alert_service.rule.basic.rangebreak.RangeBreakEvent.BarPositioned;
import com.alert_service.rule.basic.rangebreak.RangeBreakEvent.RangeCaptured;
import com.alert_service.rule.basic.rangebreak.RangeBreakEvent.RangeUnavailable;
import com.alert_service.rule.basic.rangebreak.RangeBreakEvent.RangeUnavailable.Reason;
import com.alert_service.rule.basic.rangebreak.RangeBreakEvent.WindowClosed;
import com.alert_service.timegrid.TimeGrid;

/** Trading day 2026-10-12 (EDT): 3-3-3 is 13:45:00-14:07:30 UTC, 3-3-4 is 14:07:30-14:30:00 UTC. */
class RangeBreakWatchTest {

	private static final LocalDate DAY = LocalDate.of(2026, 10, 12);

	private final RangeBreakWatch rule = new RangeBreakWatch(RuleKey.of("RANGE_BREAK_WATCH", "ES"), "ES",
			TimeGrid.cme());
	private final List<RangeBreakEvent> events = new ArrayList<>();

	{
		rule.subscribe("test", events::add);
	}

	private void feed(String instant, String high, String low) {
		rule.onEvent(bar("ES", instant, high, low));
	}

	@Test
	void capturesRangeAndReportsWatchWindow() {
		feed("2026-10-12T13:00:00Z", "6020", "6019");
		feed("2026-10-12T13:45:00Z", "6032.00", "6028.00");
		feed("2026-10-12T13:50:00Z", "6030.00", "6026.00");
		feed("2026-10-12T14:07:29Z", "6031.00", "6027.00");
		assertThat(events).isEmpty();

		feed("2026-10-12T14:07:30Z", "6031.00", "6029.00");
		feed("2026-10-12T14:08:00Z", "6032.25", "6030.00");
		feed("2026-10-12T14:09:00Z", "6028.00", "6025.75");
		feed("2026-10-12T14:30:00Z", "6030", "6030");
		feed("2026-10-12T14:31:00Z", "6030", "6030");

		assertThat(events).containsExactly(
				new RangeCaptured("ES", DAY, Instant.parse("2026-10-12T14:07:30Z"), new BigDecimal("6032.00"),
						new BigDecimal("6026.00"), new BigDecimal("6029.00")),
				new BarPositioned("ES", DAY, Instant.parse("2026-10-12T14:07:30Z"), new BigDecimal("6031.00"),
						new BigDecimal("6029.00"), false, false),
				new BarPositioned("ES", DAY, Instant.parse("2026-10-12T14:08:00Z"), new BigDecimal("6032.25"),
						new BigDecimal("6030.00"), true, false),
				new BarPositioned("ES", DAY, Instant.parse("2026-10-12T14:09:00Z"), new BigDecimal("6028.00"),
						new BigDecimal("6025.75"), false, true),
				new WindowClosed("ES", DAY, Instant.parse("2026-10-12T14:30:00Z")));
	}

	@Test
	void meanIsExactMidpoint() {
		feed("2026-10-12T13:00:00Z", "1", "1");
		feed("2026-10-12T13:45:00Z", "2185.00", "2178.10");
		feed("2026-10-12T14:07:30Z", "2180", "2180");

		assertThat(((RangeCaptured) events.getFirst()).mean()).isEqualByComparingTo("2181.55");
	}

	@Test
	void firstBarAtRangeStartIsALateStart() {
		feed("2026-10-12T13:45:00Z", "6032", "6028");
		feed("2026-10-12T14:00:00Z", "6032", "6028");
		assertThat(events).isEmpty();

		feed("2026-10-12T14:07:31Z", "6031", "6029");
		feed("2026-10-12T14:08:00Z", "6031", "6029");

		assertThat(events).containsExactly(
				new RangeUnavailable("ES", DAY, Instant.parse("2026-10-12T14:07:31Z"), Reason.LATE_START));
	}

	@Test
	void startDuringWatchWindowReportsUnavailableAtOnce() {
		feed("2026-10-12T14:15:00Z", "6031", "6029");

		assertThat(events).containsExactly(
				new RangeUnavailable("ES", DAY, Instant.parse("2026-10-12T14:15:00Z"), Reason.LATE_START));
	}

	@Test
	void noBarsInRangeBlock() {
		feed("2026-10-12T13:00:00Z", "6020", "6019");
		feed("2026-10-12T14:08:00Z", "6031", "6029");

		assertThat(events).containsExactly(
				new RangeUnavailable("ES", DAY, Instant.parse("2026-10-12T14:08:00Z"), Reason.NO_BARS_IN_RANGE));
	}

	@Test
	void noBarsInWatchBlockStillClosesTheWindow() {
		feed("2026-10-12T13:00:00Z", "6020", "6019");
		feed("2026-10-12T13:46:00Z", "6032", "6028");
		feed("2026-10-12T14:31:00Z", "6031", "6029");

		assertThat(events).hasSize(2);
		assertThat(events.get(0)).isInstanceOf(RangeCaptured.class);
		assertThat(events.get(1)).isInstanceOf(WindowClosed.class);
	}

	@Test
	void nextTradingDayStartsFresh() {
		feed("2026-10-12T14:15:00Z", "6031", "6029");
		events.clear();

		feed("2026-10-12T22:00:00Z", "6030", "6030");
		feed("2026-10-13T13:45:00Z", "6040", "6030");
		feed("2026-10-13T14:07:30Z", "6035", "6035");

		assertThat(events).hasSize(2);
		RangeCaptured captured = (RangeCaptured) events.getFirst();
		assertThat(captured.tradingDay()).isEqualTo(LocalDate.of(2026, 10, 13));
		assertThat(captured.mean()).isEqualByComparingTo("6035");
	}
}

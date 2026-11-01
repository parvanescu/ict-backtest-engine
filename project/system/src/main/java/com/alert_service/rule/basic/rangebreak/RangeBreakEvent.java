package com.alert_service.rule.basic.rangebreak;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Facts reported by {@link RangeBreakWatch}, in this order per trading day: {@link RangeCaptured} or
 * {@link RangeUnavailable}, then {@link BarPositioned} for every bar of the watch window, then {@link WindowClosed}.
 */
public sealed interface RangeBreakEvent {

	String symbol();

	LocalDate tradingDay();

	/** Start of the bar that produced the fact (UTC). */
	Instant time();

	/** The range block is over: its high, low and mean are frozen. */
	record RangeCaptured(String symbol, LocalDate tradingDay, Instant time, BigDecimal high, BigDecimal low,
			BigDecimal mean) implements RangeBreakEvent {
	}

	/** The rule has no range today. */
	record RangeUnavailable(String symbol, LocalDate tradingDay, Instant time, Reason reason)
			implements RangeBreakEvent {

		public enum Reason {
			/** The rule's first bar of the day came after the range block started. */
			LATE_START,
			/** No bar at all during the range block. */
			NO_BARS_IN_RANGE
		}
	}

	/** One bar of the watch window, and whether it broke the frozen high or low. */
	record BarPositioned(String symbol, LocalDate tradingDay, Instant time, BigDecimal high, BigDecimal low,
			boolean brokeHigh, boolean brokeLow) implements RangeBreakEvent {

		public boolean broke() {
			return brokeHigh || brokeLow;
		}
	}

	/** First bar after the watch window. */
	record WindowClosed(String symbol, LocalDate tradingDay, Instant time) implements RangeBreakEvent {
	}
}

package com.alert_service.timegrid;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Where a UTC instant falls in the quarterly grid.
 *
 * @param tradingDay  CME trade date of the trading day (date of its 18:00 New York start + 1 day)
 * @param dayStart    UTC instant of the trading day's 18:00 New York start
 * @param offsetNanos nanoseconds from {@code dayStart}, in {@code [0, 24h)}
 */
public record GridPosition(LocalDate tradingDay, Instant dayStart, long offsetNanos) {

	public GridPosition {
		if (offsetNanos < 0 || offsetNanos >= GridLevel.DAY_NANOS) {
			throw new IllegalArgumentException("offset outside the trading day: " + offsetNanos);
		}
	}

	/** Block number within the day at {@code level}: 0-3 for L1, 0-15 for L2, 0-63 for L3, 0-255 for L4. */
	public int block(GridLevel level) {
		return (int) (offsetNanos / level.lengthNanos());
	}

	/** Quarter (0-3, i.e. Q1-Q4) within the parent window at {@code level}. */
	public int quarter(GridLevel level) {
		return block(level) % 4;
	}

	/** Inclusive UTC start of the window containing this position at {@code level}. */
	public Instant windowStart(GridLevel level) {
		return dayStart.plusNanos(block(level) * level.lengthNanos());
	}

	/** Exclusive UTC end of the window containing this position at {@code level}. */
	public Instant windowEnd(GridLevel level) {
		return windowStart(level).plusNanos(level.lengthNanos());
	}

	/** UTC start of block {@code block} at {@code level} of this trading day. */
	public Instant blockStart(GridLevel level, int block) {
		return dayStart.plusNanos(block * level.lengthNanos());
	}
}

package com.alert_service.timegrid;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Maps UTC instants to their {@link GridPosition}. Pure: no state, no clock.
 * <p>
 * The trading day starts at a fixed local time in a time zone (18:00 New York, the CME Globex open).
 * The zone is only used to find the instant of that start; everything inside the day is a plain offset.
 */
public final class TimeGrid {

	public static final ZoneId NEW_YORK = ZoneId.of("America/New_York");
	public static final LocalTime CME_OPEN = LocalTime.of(18, 0);

	private final ZoneId zone;
	private final LocalTime dayStartTime;

	public TimeGrid(ZoneId zone, LocalTime dayStartTime) {
		this.zone = zone;
		this.dayStartTime = dayStartTime;
	}

	public static TimeGrid cme() {
		return new TimeGrid(NEW_YORK, CME_OPEN);
	}

	/**
	 * @throws IllegalArgumentException for the extra hour of the 25-hour weekend span when clocks go back
	 *                                  (Sunday 17:00-18:00 New York, market closed), which belongs to no trading day
	 */
	public GridPosition position(Instant instant) {
		ZonedDateTime local = instant.atZone(zone);
		LocalDate startDate = local.toLocalTime().isBefore(dayStartTime)
				? local.toLocalDate().minusDays(1)
				: local.toLocalDate();
		Instant dayStart = startDate.atTime(dayStartTime).atZone(zone).toInstant();
		long offset = Duration.between(dayStart, instant).toNanos();
		if (offset >= GridLevel.DAY_NANOS) {
			throw new IllegalArgumentException(instant + " falls in no trading day");
		}
		return new GridPosition(startDate.plusDays(1), dayStart, offset);
	}

	public GridPosition positionOfEpochNanos(long epochNanos) {
		return position(Instant.ofEpochSecond(Math.floorDiv(epochNanos, 1_000_000_000L),
				Math.floorMod(epochNanos, 1_000_000_000L)));
	}
}

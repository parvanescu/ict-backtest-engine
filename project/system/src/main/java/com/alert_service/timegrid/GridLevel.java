package com.alert_service.timegrid;

import java.time.Duration;

/**
 * The nested quarter levels of a trading day. Each level is a quarter of the one above it.
 */
public enum GridLevel {

	L1_6H(Duration.ofHours(6)),
	L2_90M(Duration.ofMinutes(90)),
	L3_22_5M(Duration.ofSeconds(1350)),
	L4_5_625M(Duration.ofMillis(337_500));

	static final long DAY_NANOS = Duration.ofHours(24).toNanos();

	private final Duration length;
	private final long lengthNanos;

	GridLevel(Duration length) {
		this.length = length;
		this.lengthNanos = length.toNanos();
	}

	public Duration length() {
		return length;
	}

	long lengthNanos() {
		return lengthNanos;
	}

	public int blocksPerDay() {
		return (int) (DAY_NANOS / lengthNanos);
	}
}

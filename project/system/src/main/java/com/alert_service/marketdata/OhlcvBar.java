package com.alert_service.marketdata;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * A completed 1-second bar covering {@code [startTime, startTime + 1 s)}.
 *
 * @param startTime bar start, UTC epoch nanoseconds
 */
public record OhlcvBar(String symbol, long startTime, BigDecimal open, BigDecimal high, BigDecimal low,
		BigDecimal close, long volume) {

	public OhlcvBar {
		Objects.requireNonNull(symbol, "symbol");
		Objects.requireNonNull(open, "open");
		Objects.requireNonNull(high, "high");
		Objects.requireNonNull(low, "low");
		Objects.requireNonNull(close, "close");
		if (high.compareTo(low) < 0) {
			throw new IllegalArgumentException("high " + high + " below low " + low);
		}
	}

	public Instant startInstant() {
		return Instant.ofEpochSecond(Math.floorDiv(startTime, 1_000_000_000L), Math.floorMod(startTime, 1_000_000_000L));
	}
}

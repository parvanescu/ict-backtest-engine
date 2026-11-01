package com.alert_service.testing;

import java.math.BigDecimal;
import java.time.Instant;

import com.alert_service.marketdata.OhlcvBar;

/** Builds 1-second bars. Times are UTC instants, e.g. {@code 2026-10-12T13:45:00Z} (09:45 New York, EDT). */
public final class Bars {

	private Bars() {
	}

	public static long nanos(String instant) {
		Instant t = Instant.parse(instant);
		return t.getEpochSecond() * 1_000_000_000L + t.getNano();
	}

	public static OhlcvBar bar(String symbol, String instant, String high, String low) {
		BigDecimal h = new BigDecimal(high);
		BigDecimal l = new BigDecimal(low);
		return new OhlcvBar(symbol, nanos(instant), l, h, l, h, 1);
	}

	public static OhlcvBar bar(String symbol, String instant, String price) {
		return bar(symbol, instant, price, price);
	}
}

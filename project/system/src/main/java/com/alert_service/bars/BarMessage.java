package com.alert_service.bars;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonInclude;

import com.alert_service.marketdata.OhlcvBar;

/**
 * A message of a bar topic: a 1-second bar ({@code type} {@code BAR}) or an error ({@code type} {@code ERROR}).
 *
 * @param time ISO-8601 UTC start of the bar
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BarMessage(String type, String symbol, String time, BigDecimal open, BigDecimal high, BigDecimal low,
		BigDecimal close, Long volume, String message) {

	public static final String BAR = "BAR";
	public static final String ERROR = "ERROR";

	public static BarMessage bar(OhlcvBar bar) {
		return new BarMessage(BAR, bar.symbol(), bar.startInstant().toString(), bar.open(), bar.high(), bar.low(),
				bar.close(), bar.volume(), null);
	}

	public static BarMessage error(String message) {
		return new BarMessage(ERROR, null, null, null, null, null, null, null, message);
	}
}

package com.alert_service.marketdata;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A ZeroMQ topic: {@code <DATATYPE>.<SYMBOL>}, e.g. {@code OHLCV.ES}.
 */
public record Topic(DataType dataType, String symbol) {

	private static final Pattern SYMBOL = Pattern.compile("[A-Z0-9]{1,16}");

	public Topic {
		Objects.requireNonNull(dataType, "dataType");
		requireValidSymbol(symbol);
	}

	public static String requireValidSymbol(String symbol) {
		if (symbol == null || !SYMBOL.matcher(symbol).matches()) {
			throw new IllegalArgumentException("Invalid symbol: " + symbol);
		}
		return symbol;
	}

	public String value() {
		return dataType.name() + "." + symbol;
	}

	@Override
	public String toString() {
		return value();
	}
}

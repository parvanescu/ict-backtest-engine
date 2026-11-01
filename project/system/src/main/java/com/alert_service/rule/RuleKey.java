package com.alert_service.rule;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Identifies a rule instance: its type and its normalized parameters (symbols, sorted and unique).
 * Same key, same instance. Also used as the instance's subscriber id on its inputs.
 */
public record RuleKey(String type, List<String> symbols) {

	public RuleKey {
		Objects.requireNonNull(type, "type");
		symbols = symbols.stream().distinct().sorted().toList();
	}

	public static RuleKey of(String type, Collection<String> symbols) {
		return new RuleKey(type, List.copyOf(symbols));
	}

	public static RuleKey of(String type, String symbol) {
		return new RuleKey(type, List.of(symbol));
	}

	@Override
	public String toString() {
		return type + "[" + String.join(",", symbols) + "]";
	}
}

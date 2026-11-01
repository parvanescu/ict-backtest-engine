package com.alert_service.rule;

/**
 * Static description of an aggregate rule type. The request adds the symbols.
 *
 * @param basicRuleType type of the basic rule it needs, one instance per symbol
 * @param inputType     type of the basic-rule facts it consumes
 * @param minSymbols    fewest symbols a request may name
 */
public record AggregateRuleDescriptor(String type, String basicRuleType, Class<?> inputType, int minSymbols) {
}

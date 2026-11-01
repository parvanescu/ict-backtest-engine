package com.alert_service.rule;

import com.alert_service.marketdata.DataRequirement;
import com.alert_service.timegrid.GridLevel;

/**
 * Static description of a basic rule type. The request adds the symbol.
 *
 * @param eventType     type of the facts the rule emits
 * @param earliestLevel with {@code earliestBlock}: the first grid block the rule must see live. If the first bar
 *                      of a trading day starts at or after it, the day is invalid for the rule.
 */
public record BasicRuleDescriptor(String type, DataRequirement data, Class<?> eventType, GridLevel earliestLevel,
		int earliestBlock) {
}

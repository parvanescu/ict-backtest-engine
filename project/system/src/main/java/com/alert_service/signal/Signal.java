package com.alert_service.signal;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

import com.alert_service.rule.RuleResult;

/**
 * A message of a signal topic. Factual only: events and the values behind them, never instructions.
 *
 * @param id        increasing per rule instance; lets a client merge the live topic with the history call
 * @param kind      {@code REPORT}, {@code STATUS}, or {@code ERROR} (invalid subscription, failure)
 * @param eventTime ISO-8601 UTC time of the bar that produced it; absent for errors
 * @param message   set for errors only
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Signal(Long id, String ruleKey, String rule, String kind, String code, String level, List<String> scope,
		String eventTime, Map<String, Object> evidence, String message) {

	public static final String ERROR = "ERROR";
	public static final String INVALID_SUBSCRIPTION = "INVALID_SUBSCRIPTION";
	public static final String INTERNAL_ERROR = "INTERNAL_ERROR";

	public static Signal of(long id, RuleResult result) {
		return new Signal(id, result.ruleKey(), result.rule(), result.kind().name(), result.code(),
				result.level() == null ? null : result.level().name(), result.scope(), result.eventTime().toString(),
				result.evidence(), null);
	}

	public static Signal error(String ruleKey, String code, String message) {
		return new Signal(null, ruleKey, null, ERROR, code, null, null, null, null, message);
	}
}

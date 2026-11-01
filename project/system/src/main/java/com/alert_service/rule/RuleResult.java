package com.alert_service.rule;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.alert_service.timegrid.GridLevel;

/**
 * What an aggregate rule emits. Whether it becomes a signal is decided outside the rule.
 *
 * @param kind      {@code RESULT} (a verdict), {@code REPORT} (a fact worth showing), {@code STATUS}
 * @param code      what a {@code REPORT} or {@code STATUS} is about, e.g. {@code EXTREMITY_PASSED},
 *                  {@code INVALID_WINDOW}; {@code null} for a {@code RESULT}
 * @param verdict   set for a {@code RESULT} only
 * @param eventTime bar time (UTC) that produced it
 * @param evidence  the numbers behind it
 */
public record RuleResult(Kind kind, String rule, String ruleKey, String code, GridLevel level, List<String> scope,
		Verdict verdict, Instant eventTime, Map<String, Object> evidence) {

	public enum Kind {
		RESULT, REPORT, STATUS
	}

	public RuleResult {
		Objects.requireNonNull(kind, "kind");
		Objects.requireNonNull(eventTime, "eventTime");
		scope = List.copyOf(scope);
		evidence = Map.copyOf(evidence);
	}

	public static RuleResult verdict(RuleKey key, GridLevel level, String symbol, Verdict verdict, Instant eventTime,
			Map<String, Object> evidence) {
		return new RuleResult(Kind.RESULT, key.type(), key.toString(), null, level, List.of(symbol), verdict,
				eventTime, evidence);
	}

	public static RuleResult report(RuleKey key, String code, GridLevel level, List<String> scope, Instant eventTime,
			Map<String, Object> evidence) {
		return new RuleResult(Kind.REPORT, key.type(), key.toString(), code, level, scope, null, eventTime, evidence);
	}

	public static RuleResult status(RuleKey key, String code, List<String> scope, Instant eventTime,
			Map<String, Object> evidence) {
		return new RuleResult(Kind.STATUS, key.type(), key.toString(), code, null, scope, null, eventTime, evidence);
	}
}

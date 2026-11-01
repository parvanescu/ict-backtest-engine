package com.alert_service.rule;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.alert_service.timegrid.GridLevel;

/**
 * What an aggregate rule emits. Whether it becomes a signal is decided outside the rule.
 *
 * @param kind      {@code REPORT} (a fact worth showing, e.g. a rule firing for an instrument) or {@code STATUS}
 * @param code      what the report or status is about, e.g. {@code RANGE_SWEEP_EVENT}, {@code EXTREMITY_PASSED},
 *                  {@code INVALID_WINDOW}
 * @param eventTime bar time (UTC) that produced it
 * @param evidence  the numbers behind it
 */
public record RuleResult(Kind kind, String rule, String ruleKey, String code, GridLevel level, List<String> scope,
		Instant eventTime, Map<String, Object> evidence) {

	public enum Kind {
		REPORT, STATUS
	}

	public RuleResult {
		Objects.requireNonNull(kind, "kind");
		Objects.requireNonNull(eventTime, "eventTime");
		scope = List.copyOf(scope);
		evidence = Map.copyOf(evidence);
	}

	public static RuleResult report(RuleKey key, String code, GridLevel level, List<String> scope, Instant eventTime,
			Map<String, Object> evidence) {
		return new RuleResult(Kind.REPORT, key.type(), key.toString(), code, level, scope, eventTime, evidence);
	}

	public static RuleResult status(RuleKey key, String code, List<String> scope, Instant eventTime,
			Map<String, Object> evidence) {
		return new RuleResult(Kind.STATUS, key.type(), key.toString(), code, null, scope, eventTime, evidence);
	}
}

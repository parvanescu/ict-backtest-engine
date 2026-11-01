package com.alert_service.rule.aggregate.sequencerangesweep;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.alert_service.rule.RuleState;
import com.alert_service.rule.basic.rangebreak.RangeBreakEvent.BarPositioned;
import com.alert_service.rule.basic.rangebreak.RangeBreakEvent.RangeCaptured;

final class SequenceRangeSweepState implements RuleState {

	enum Side {
		HIGH, LOW
	}

	/** A break by {@code trigger} at {@code second}, waiting to be tested against {@code target}. */
	record PendingCheck(BarPositioned trigger, Side side, String target) {

		Instant second() {
			return trigger.time();
		}
	}

	final LocalDate tradingDay;
	final Map<String, RangeCaptured> ranges = new HashMap<>();
	/** Symbols that will have no range today: unavailable, or captured before this rule attached. */
	final Set<String> missingRanges = new HashSet<>();
	final Map<String, Deque<BarPositioned>> recent = new HashMap<>();
	final Set<String> closed = new HashSet<>();
	final List<PendingCheck> pending = new ArrayList<>();
	final Set<String> signalled = new HashSet<>();
	boolean invalid;

	SequenceRangeSweepState(LocalDate tradingDay) {
		this.tradingDay = tradingDay;
	}

	Deque<BarPositioned> recentOf(String symbol) {
		return recent.computeIfAbsent(symbol, s -> new ArrayDeque<>());
	}

	@Override
	public LocalDate tradingDay() {
		return tradingDay;
	}
}

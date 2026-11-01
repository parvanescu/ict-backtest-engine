package com.alert_service.rule.aggregate.sequencerangesweep;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.alert_service.rule.AggregateRule;
import com.alert_service.rule.AggregateRuleDescriptor;
import com.alert_service.rule.DayStatus;
import com.alert_service.rule.RuleKey;
import com.alert_service.rule.RuleResult;
import com.alert_service.rule.aggregate.sequencerangesweep.SequenceRangeSweepState.PendingCheck;
import com.alert_service.rule.aggregate.sequencerangesweep.SequenceRangeSweepState.Side;
import com.alert_service.rule.basic.rangebreak.RangeBreakEvent;
import com.alert_service.rule.basic.rangebreak.RangeBreakEvent.BarPositioned;
import com.alert_service.rule.basic.rangebreak.RangeBreakEvent.RangeCaptured;
import com.alert_service.rule.basic.rangebreak.RangeBreakEvent.RangeUnavailable;
import com.alert_service.rule.basic.rangebreak.RangeBreakEvent.WindowClosed;
import com.alert_service.rule.basic.rangebreak.RangeBreakWatch;

/**
 * When one instrument X breaks its 3-3-3 high or low during 3-3-4, every other instrument Y whose bar at the same
 * second is in the matching half of its own range gets an event report: after a low break when
 * {@code L <= low < M}, after a high break when {@code M < high <= H}. One event per target per day.
 * <p>
 * Same-second resolution uses event time only. Bars of different symbols arrive in any order, so a check of Y at
 * second s is decided by Y's own stream: its bar at s if it has one, otherwise its latest bar before s once a later
 * bar of Y (or the end of the window) shows that Y had no bar at s.
 * <p>
 * The day is judged once the range status of every symbol is known (captured, or missing because a basic rule
 * started late or this rule attached after the range was captured). Breaks seen before that are held and
 * evaluated then; if any range is missing the day is reported {@code INVALID_WINDOW}.
 */
public final class SequenceRangeSweep extends AggregateRule<RangeBreakEvent, SequenceRangeSweepState> {

	public static final AggregateRuleDescriptor DESCRIPTOR = new AggregateRuleDescriptor("SEQUENCE_RANGE_SWEEP",
			RangeBreakWatch.DESCRIPTOR.type(), RangeBreakEvent.class, 2);

	public static final String RANGE_SWEEP_EVENT = "RANGE_SWEEP_EVENT";
	/**
	 * A target was checked after a break and did not qualify. Same evidence as {@link #RANGE_SWEEP_EVENT}. Not
	 * emitted for a target that was already signalled, had no bar up to the second, or broke its own range then.
	 */
	public static final String RANGE_SWEEP_NO_EVENT = "RANGE_SWEEP_NO_EVENT";
	public static final String EXTREMITY_PASSED = "EXTREMITY_PASSED";
	public static final String INVALID_WINDOW = "INVALID_WINDOW";

	static final int RECENT_BARS = 64;

	private final List<String> symbols;

	public SequenceRangeSweep(RuleKey key) {
		super(key);
		this.symbols = key.symbols();
	}

	@Override
	protected LocalDate tradingDayOf(RangeBreakEvent event) {
		return event.tradingDay();
	}

	@Override
	protected SequenceRangeSweepState newState(LocalDate tradingDay) {
		return new SequenceRangeSweepState(tradingDay);
	}

	@Override
	protected void process(RangeBreakEvent event) {
		if (state.invalid || !symbols.contains(event.symbol())) {
			return;
		}
		switch (event) {
			case RangeCaptured captured -> state.ranges.put(captured.symbol(), captured);
			case RangeUnavailable unavailable -> state.missingRanges.add(unavailable.symbol());
			case BarPositioned bar -> onBar(bar);
			case WindowClosed closed -> {
				markMissedRange(closed.symbol());
				state.closed.add(closed.symbol());
			}
		}
		if (dayStatus() == DayStatus.UNKNOWN) {
			judgeDay(event);
		}
		if (dayStatus() == DayStatus.VALID) {
			resolvePending();
		}
	}

	private void onBar(BarPositioned bar) {
		markMissedRange(bar.symbol());
		Deque<BarPositioned> recent = state.recentOf(bar.symbol());
		recent.addLast(bar);
		if (recent.size() > RECENT_BARS) {
			recent.removeFirst();
		}
		if (bar.brokeHigh()) {
			trigger(bar, Side.HIGH);
		}
		if (bar.brokeLow()) {
			trigger(bar, Side.LOW);
		}
	}

	/** A watch-window event without a prior range means the range was captured before this rule attached. */
	private void markMissedRange(String symbol) {
		if (!state.ranges.containsKey(symbol)) {
			state.missingRanges.add(symbol);
		}
	}

	private void trigger(BarPositioned bar, Side side) {
		RangeCaptured range = state.ranges.get(bar.symbol());
		if (range != null) {
			emit(RuleResult.report(key(), EXTREMITY_PASSED, RangeBreakWatch.LEVEL, List.of(bar.symbol()), bar.time(),
					Map.of("side", side.name(), "price", side == Side.HIGH ? bar.high() : bar.low(), "level",
							side == Side.HIGH ? range.high() : range.low())));
		}
		for (String target : symbols) {
			if (!target.equals(bar.symbol())) {
				state.pending.add(new PendingCheck(bar, side, target));
			}
		}
	}

	private void judgeDay(RangeBreakEvent event) {
		if (state.ranges.size() + state.missingRanges.size() < symbols.size()) {
			return;
		}
		if (state.missingRanges.isEmpty()) {
			dayStatus(DayStatus.VALID);
			return;
		}
		dayStatus(DayStatus.INVALID_WINDOW);
		state.invalid = true;
		state.pending.clear();
		List<String> missing = state.missingRanges.stream().sorted().toList();
		emit(RuleResult.status(key(), INVALID_WINDOW, missing, event.time(),
				Map.of("tradingDay", state.tradingDay.toString(), "missingRanges", missing)));
	}

	private void resolvePending() {
		state.pending.sort(Comparator.comparing(PendingCheck::second));
		for (Iterator<PendingCheck> it = state.pending.iterator(); it.hasNext();) {
			PendingCheck check = it.next();
			if (state.signalled.contains(check.target())) {
				it.remove();
				continue;
			}
			TargetBar target = targetBar(check);
			if (target.decided()) {
				it.remove();
				if (target.bar() != null) {
					test(check, target.bar());
				}
			}
		}
	}

	/**
	 * The bar a check is decided on. {@code bar} is null when decided without one: the target has no bar up to
	 * that second, or it broke its own range at that second and is therefore a trigger, not a target.
	 */
	private record TargetBar(boolean decided, BarPositioned bar) {

		static final TargetBar UNDECIDED = new TargetBar(false, null);

		static TargetBar of(BarPositioned bar) {
			return new TargetBar(true, bar);
		}
	}

	private TargetBar targetBar(PendingCheck check) {
		Deque<BarPositioned> recent = state.recentOf(check.target());
		BarPositioned latestBefore = null;
		for (BarPositioned bar : recent) {
			int order = bar.time().compareTo(check.second());
			if (order == 0) {
				return TargetBar.of(bar.broke() ? null : bar);
			}
			if (order < 0) {
				latestBefore = bar;
			}
		}
		boolean targetPassedSecond = !recent.isEmpty() && recent.getLast().time().isAfter(check.second());
		if (targetPassedSecond || state.closed.contains(check.target())) {
			return TargetBar.of(latestBefore);
		}
		return TargetBar.UNDECIDED;
	}

	private void test(PendingCheck check, BarPositioned bar) {
		RangeCaptured range = state.ranges.get(check.target());
		boolean qualifies = check.side() == Side.LOW
				? range.low().compareTo(bar.low()) <= 0 && bar.low().compareTo(range.mean()) < 0
				: range.mean().compareTo(bar.high()) < 0 && bar.high().compareTo(range.high()) <= 0;
		if (qualifies) {
			state.signalled.add(check.target());
		}
		Map<String, Object> trigger = new LinkedHashMap<>();
		trigger.put("symbol", check.trigger().symbol());
		trigger.put("side", check.side().name());
		trigger.put("price", check.side() == Side.LOW ? check.trigger().low() : check.trigger().high());
		Map<String, Object> target = new LinkedHashMap<>();
		target.put("barTime", bar.time().toString());
		target.put(check.side() == Side.LOW ? "low" : "high", check.side() == Side.LOW ? bar.low() : bar.high());
		target.put("H", range.high());
		target.put("L", range.low());
		target.put("M", range.mean());
		emit(RuleResult.report(key(), qualifies ? RANGE_SWEEP_EVENT : RANGE_SWEEP_NO_EVENT, RangeBreakWatch.LEVEL,
				List.of(check.target()), check.second(), Map.of("trigger", trigger, "target", target)));
	}
}

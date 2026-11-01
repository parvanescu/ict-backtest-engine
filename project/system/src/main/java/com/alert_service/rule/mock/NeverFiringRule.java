package com.alert_service.rule.mock;

import java.time.LocalDate;

import com.alert_service.rule.AggregateRule;
import com.alert_service.rule.AggregateRuleDescriptor;
import com.alert_service.rule.RuleKey;
import com.alert_service.rule.RuleState;
import com.alert_service.rule.basic.rangebreak.RangeBreakEvent;
import com.alert_service.rule.basic.rangebreak.RangeBreakWatch;

/**
 * Consumes every fact of one basic rule per symbol and never emits anything. Shows that a rule plugs in without
 * touching the core, and exercises the subscription chain end to end.
 */
public final class NeverFiringRule extends AggregateRule<RangeBreakEvent, NeverFiringRule.State> {

	public static final AggregateRuleDescriptor DESCRIPTOR = new AggregateRuleDescriptor("NEVER_FIRING",
			RangeBreakWatch.DESCRIPTOR.type(), RangeBreakEvent.class, 1);

	public NeverFiringRule(RuleKey key) {
		super(key);
	}

	@Override
	protected LocalDate tradingDayOf(RangeBreakEvent event) {
		return event.tradingDay();
	}

	@Override
	protected State newState(LocalDate tradingDay) {
		return new State(tradingDay);
	}

	@Override
	protected void process(RangeBreakEvent event) {
		state.eventsSeen++;
	}

	static final class State implements RuleState {

		private final LocalDate tradingDay;
		long eventsSeen;

		State(LocalDate tradingDay) {
			this.tradingDay = tradingDay;
		}

		@Override
		public LocalDate tradingDay() {
			return tradingDay;
		}
	}
}

package com.alert_service.rule.basic.rangebreak;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.alert_service.rule.RuleState;

final class RangeBreakWatchState implements RuleState {

	enum Phase {
		WAITING, CAPTURING, WATCHING, DONE
	}

	final LocalDate tradingDay;
	Phase phase = Phase.WAITING;
	BigDecimal runningHigh;
	BigDecimal runningLow;
	BigDecimal high;
	BigDecimal low;
	BigDecimal mean;

	RangeBreakWatchState(LocalDate tradingDay) {
		this.tradingDay = tradingDay;
	}

	@Override
	public LocalDate tradingDay() {
		return tradingDay;
	}
}

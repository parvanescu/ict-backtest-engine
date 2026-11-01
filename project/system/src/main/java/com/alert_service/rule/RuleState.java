package com.alert_service.rule;

import java.time.LocalDate;

/**
 * The data of a rule for one trading day: captured values, flags, pending work. No logic.
 * Owned by exactly one {@link RuleInstance} and only changed by it, on the instance's thread.
 */
public interface RuleState {

	LocalDate tradingDay();
}

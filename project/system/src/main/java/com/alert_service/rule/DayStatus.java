package com.alert_service.rule;

public enum DayStatus {

	/** Not decided yet for the current trading day. */
	UNKNOWN,
	VALID,
	/** The rule started too late to have the data it needs today. */
	INVALID_WINDOW
}

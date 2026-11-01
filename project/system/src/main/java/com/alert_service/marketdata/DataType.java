package com.alert_service.marketdata;

public enum DataType {

	OHLCV(OhlcvBar.class);

	private final Class<?> eventType;

	DataType(Class<?> eventType) {
		this.eventType = eventType;
	}

	public Class<?> eventType() {
		return eventType;
	}
}

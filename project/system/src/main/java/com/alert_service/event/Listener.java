package com.alert_service.event;

@FunctionalInterface
public interface Listener<E> {

	void onEvent(E event);
}

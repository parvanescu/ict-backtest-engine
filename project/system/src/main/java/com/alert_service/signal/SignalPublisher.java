package com.alert_service.signal;

/**
 * Delivers signals to the users of one rule instance.
 */
@FunctionalInterface
public interface SignalPublisher {

	void publish(String ruleKey, Signal signal);
}

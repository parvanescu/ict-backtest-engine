package com.alert_service.api;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import com.alert_service.signal.Signal;
import com.alert_service.signal.SignalPublisher;

/**
 * Publishes a rule instance's signals to {@code /topic/signals/{ruleKey}}; the broker delivers them to every
 * session subscribed to that topic.
 */
@Component
public class StompSignalPublisher implements SignalPublisher {

	private final SimpMessagingTemplate messaging;

	public StompSignalPublisher(SimpMessagingTemplate messaging) {
		this.messaging = messaging;
	}

	@Override
	public void publish(String ruleKey, Signal signal) {
		messaging.convertAndSend(StompConfig.SIGNALS_TOPIC + ruleKey, signal);
	}
}

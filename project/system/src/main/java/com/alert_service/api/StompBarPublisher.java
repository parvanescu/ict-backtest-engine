package com.alert_service.api;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import com.alert_service.bars.BarMessage;
import com.alert_service.bars.BarPublisher;

/**
 * Publishes live bars to {@code /topic/bars/{symbol}}.
 */
@Component
public class StompBarPublisher implements BarPublisher {

	private final SimpMessagingTemplate messaging;

	public StompBarPublisher(SimpMessagingTemplate messaging) {
		this.messaging = messaging;
	}

	@Override
	public void publish(String symbol, BarMessage message) {
		messaging.convertAndSend(StompConfig.BARS_TOPIC + symbol, message);
	}
}

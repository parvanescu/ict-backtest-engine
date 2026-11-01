package com.alert_service.bars;

/**
 * Delivers live bars to the users watching a symbol.
 */
@FunctionalInterface
public interface BarPublisher {

	void publish(String symbol, BarMessage message);
}

package com.alert_service.marketdata;

/**
 * Turns the payload frame of a message into a typed event. One decoder per {@link DataType}.
 */
public interface MarketDataDecoder<E> {

	DataType dataType();

	/**
	 * @throws InvalidMessageException if the payload is malformed or does not belong to {@code topic}
	 */
	E decode(Topic topic, byte[] payload);
}

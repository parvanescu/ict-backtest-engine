package com.alert_service.marketdata.zmq;

import com.alert_service.marketdata.MarketDataDecoder;
import com.alert_service.marketdata.Topic;

@FunctionalInterface
public interface DataProviderFactory {

	/**
	 * Creates and starts a provider.
	 *
	 * @param onEmpty to run when the provider's last listener leaves
	 */
	<E> DataProvider<E> create(Topic topic, MarketDataDecoder<E> decoder, Runnable onEmpty);
}

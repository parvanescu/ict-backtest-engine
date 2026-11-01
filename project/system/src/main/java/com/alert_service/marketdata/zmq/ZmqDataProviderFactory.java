package com.alert_service.marketdata.zmq;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.zeromq.ZContext;

import com.alert_service.marketdata.MarketDataDecoder;
import com.alert_service.marketdata.Topic;

/**
 * Creates {@link ZmqDataProvider}s sharing one ZeroMQ context, and closes them all on shutdown.
 */
public class ZmqDataProviderFactory implements DataProviderFactory, AutoCloseable {

	private static final long SHUTDOWN_WAIT_MILLIS = 2_000;

	private final ZContext context = new ZContext();
	private final ZmqProperties properties;
	private final Set<ZmqDataProvider<?>> created = ConcurrentHashMap.newKeySet();

	public ZmqDataProviderFactory(ZmqProperties properties) {
		this.properties = properties;
	}

	@Override
	public <E> DataProvider<E> create(Topic topic, MarketDataDecoder<E> decoder, Runnable onEmpty) {
		ZmqDataProvider<E> provider = new ZmqDataProvider<>(topic, decoder, onEmpty, context,
				properties.endpoints(), (int) properties.receiveTimeout().toMillis());
		created.removeIf(stopped -> !stopped.isAlive());
		created.add(provider);
		provider.start();
		return provider;
	}

	@Override
	public void close() throws InterruptedException {
		created.forEach(ZmqDataProvider::close);
		for (ZmqDataProvider<?> provider : created) {
			provider.join(SHUTDOWN_WAIT_MILLIS);
		}
		context.close();
	}
}

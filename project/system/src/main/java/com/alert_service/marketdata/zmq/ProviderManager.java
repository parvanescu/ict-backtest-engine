package com.alert_service.marketdata.zmq;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.alert_service.event.Listener;
import com.alert_service.event.Subscription;
import com.alert_service.marketdata.DataType;
import com.alert_service.marketdata.MarketDataDecoder;
import com.alert_service.marketdata.Topic;

/**
 * Creates, shares and closes providers: one per topic, closed when its last listener leaves.
 * Get-or-create and close-when-empty run under one lock.
 */
public class ProviderManager {

	private final DataProviderFactory factory;
	private final Map<DataType, MarketDataDecoder<?>> decoders = new EnumMap<>(DataType.class);
	private final Map<Topic, DataProvider<?>> providers = new HashMap<>();

	public ProviderManager(DataProviderFactory factory, List<MarketDataDecoder<?>> decoders) {
		this.factory = factory;
		decoders.forEach(decoder -> this.decoders.put(decoder.dataType(), decoder));
	}

	public synchronized <E> Subscription subscribe(Topic topic, Class<E> eventType, String subscriberId,
			Listener<? super E> listener) {
		if (topic.dataType().eventType() != eventType) {
			throw new IllegalArgumentException(topic + " carries " + topic.dataType().eventType().getSimpleName()
					+ ", not " + eventType.getSimpleName());
		}
		@SuppressWarnings("unchecked")
		DataProvider<E> provider = (DataProvider<E>) providers.computeIfAbsent(topic, this::create);
		return provider.subscribe(subscriberId, listener);
	}

	public synchronized Set<Topic> topics() {
		return Set.copyOf(providers.keySet());
	}

	private DataProvider<?> create(Topic topic) {
		MarketDataDecoder<?> decoder = decoders.get(topic.dataType());
		if (decoder == null) {
			throw new IllegalStateException("No decoder for " + topic.dataType());
		}
		return factory.create(topic, decoder, () -> retire(topic));
	}

	/**
	 * A provider gets its first listener in the same locked call that creates it, so an empty provider found
	 * here is one whose last listener left.
	 */
	private synchronized void retire(Topic topic) {
		DataProvider<?> provider = providers.get(topic);
		if (provider != null && !provider.hasListeners()) {
			providers.remove(topic);
			provider.close();
		}
	}
}

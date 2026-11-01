package com.alert_service.testing;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import com.alert_service.event.Listener;
import com.alert_service.event.ListenerList;
import com.alert_service.event.Subscription;
import com.alert_service.marketdata.MarketDataDecoder;
import com.alert_service.marketdata.Topic;
import com.alert_service.marketdata.zmq.DataProvider;
import com.alert_service.marketdata.zmq.DataProviderFactory;

/** Creates in-memory providers that tests push events into. */
public final class FakeDataProviderFactory implements DataProviderFactory {

	private final List<FakeProvider<?>> created = new CopyOnWriteArrayList<>();
	private final Map<Topic, FakeProvider<?>> open = new ConcurrentHashMap<>();

	@Override
	public <E> DataProvider<E> create(Topic topic, MarketDataDecoder<E> decoder, Runnable onEmpty) {
		FakeProvider<E> provider = new FakeProvider<>(topic, onEmpty);
		created.add(provider);
		open.put(topic, provider);
		return provider;
	}

	public List<FakeProvider<?>> created() {
		return created;
	}

	@SuppressWarnings("unchecked")
	public <E> FakeProvider<E> open(Topic topic) {
		FakeProvider<?> provider = open.get(topic);
		if (provider == null) {
			throw new IllegalStateException("No open provider for " + topic);
		}
		return (FakeProvider<E>) provider;
	}

	public boolean isOpen(Topic topic) {
		return open.containsKey(topic);
	}

	public final class FakeProvider<E> implements DataProvider<E> {

		private final Topic topic;
		private final ListenerList<E> listeners;
		private volatile boolean closed;

		private FakeProvider(Topic topic, Runnable onEmpty) {
			this.topic = topic;
			this.listeners = new ListenerList<>(topic.value(), onEmpty);
		}

		public void push(E event) {
			listeners.dispatch(event);
		}

		public boolean isClosed() {
			return closed;
		}

		@Override
		public Topic topic() {
			return topic;
		}

		@Override
		public Subscription subscribe(String subscriberId, Listener<? super E> listener) {
			return listeners.subscribe(subscriberId, listener);
		}

		@Override
		public boolean hasListeners() {
			return !listeners.isEmpty();
		}

		@Override
		public void close() {
			closed = true;
			open.remove(topic, this);
		}
	}
}

package com.alert_service.marketdata.zmq;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMsg;

import com.alert_service.event.Listener;
import com.alert_service.event.ListenerList;
import com.alert_service.event.Subscription;
import com.alert_service.marketdata.InvalidMessageException;
import com.alert_service.marketdata.MarketDataDecoder;
import com.alert_service.marketdata.Topic;

/**
 * One SUB socket for one topic. The socket is created, used and closed on the provider's own thread, since
 * ZeroMQ sockets are not thread-safe. Listeners are called on that thread.
 */
public final class ZmqDataProvider<E> implements DataProvider<E> {

	private static final Logger log = LoggerFactory.getLogger(ZmqDataProvider.class);

	private final Topic topic;
	private final MarketDataDecoder<E> decoder;
	private final ListenerList<E> listeners;
	private final Thread thread;
	private volatile boolean running = true;

	ZmqDataProvider(Topic topic, MarketDataDecoder<E> decoder, Runnable onEmpty, ZContext context,
			List<String> endpoints, int receiveTimeoutMillis) {
		this.topic = topic;
		this.decoder = decoder;
		this.listeners = new ListenerList<>(topic.value(), onEmpty);
		this.thread = Thread.ofPlatform()
			.name("zmq-" + topic.value())
			.daemon()
			.unstarted(() -> receiveLoop(context, endpoints, receiveTimeoutMillis));
	}

	void start() {
		thread.start();
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
		running = false;
	}

	boolean isAlive() {
		return thread.isAlive();
	}

	void join(long millis) throws InterruptedException {
		thread.join(millis);
	}

	private void receiveLoop(ZContext context, List<String> endpoints, int receiveTimeoutMillis) {
		ZMQ.Socket socket = context.createSocket(SocketType.SUB);
		try {
			socket.setReceiveTimeOut(receiveTimeoutMillis);
			endpoints.forEach(socket::connect);
			socket.subscribe(topic.value().getBytes(StandardCharsets.UTF_8));
			log.info("Provider {} connected to {}", topic, endpoints);
			while (running && !Thread.currentThread().isInterrupted()) {
				ZMsg message = ZMsg.recvMsg(socket);
				if (message == null) {
					continue;
				}
				if (message.size() != 2) {
					log.warn("Provider {} dropped a message with {} frames", topic, message.size());
					continue;
				}
				handle(message.popString(), message.pop().getData());
			}
		}
		catch (RuntimeException ex) {
			if (running) {
				log.error("Provider {} stopped unexpectedly", topic, ex);
			}
		}
		finally {
			socket.close();
			log.info("Provider {} closed", topic);
		}
	}

	/**
	 * Validates and decodes one message and dispatches it. ZeroMQ matches subscriptions by prefix, so a topic
	 * frame that is not exactly this provider's topic (e.g. {@code OHLCV.ES2}) is dropped.
	 */
	void handle(String topicFrame, byte[] payload) {
		if (!topic.value().equals(topicFrame)) {
			return;
		}
		E event;
		try {
			event = decoder.decode(topic, payload);
		}
		catch (InvalidMessageException ex) {
			log.warn("Provider {} dropped an invalid message: {}", topic, ex.getMessage());
			return;
		}
		listeners.dispatch(event);
	}
}

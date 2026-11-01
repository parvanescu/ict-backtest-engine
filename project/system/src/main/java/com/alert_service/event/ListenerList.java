package com.alert_service.event;

import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The listener list every source uses.
 * <p>
 * Adds and removes are safe from any thread; {@link #dispatch} iterates over a snapshot, so a listener added
 * during a dispatch receives events from the next one on, and a listener removed during a dispatch may still
 * receive the event in flight. A failing listener is logged and does not stop the others.
 */
public final class ListenerList<E> implements Source<E> {

	private static final Logger log = LoggerFactory.getLogger(ListenerList.class);

	private final String owner;
	private final Runnable onEmpty;
	private final CopyOnWriteArrayList<Entry> entries = new CopyOnWriteArrayList<>();

	/**
	 * @param owner   name of the owning source, for logs
	 * @param onEmpty run (outside any lock of this list) when a removal leaves the list empty
	 */
	public ListenerList(String owner, Runnable onEmpty) {
		this.owner = owner;
		this.onEmpty = onEmpty;
	}

	public ListenerList(String owner) {
		this(owner, () -> {
		});
	}

	@Override
	public synchronized Subscription subscribe(String subscriberId, Listener<? super E> listener) {
		for (Entry entry : entries) {
			if (entry.subscriberId.equals(subscriberId) && !entry.isClosed()) {
				return entry;
			}
		}
		Entry entry = new Entry(subscriberId, listener);
		entries.add(entry);
		return entry;
	}

	public void dispatch(E event) {
		for (Entry entry : entries) {
			try {
				entry.listener.onEvent(event);
			}
			catch (RuntimeException ex) {
				log.error("Listener {} of {} failed on {}", entry.subscriberId, owner, event, ex);
			}
		}
	}

	public boolean isEmpty() {
		return entries.isEmpty();
	}

	public int size() {
		return entries.size();
	}

	private void remove(Entry entry) {
		boolean nowEmpty;
		synchronized (this) {
			nowEmpty = entries.remove(entry) && entries.isEmpty();
		}
		if (nowEmpty) {
			onEmpty.run();
		}
	}

	private final class Entry implements Subscription {

		private final String id = UUID.randomUUID().toString();
		private final String subscriberId;
		private final Listener<? super E> listener;
		private final AtomicBoolean closed = new AtomicBoolean();

		private Entry(String subscriberId, Listener<? super E> listener) {
			this.subscriberId = subscriberId;
			this.listener = listener;
		}

		@Override
		public String id() {
			return id;
		}

		@Override
		public String subscriberId() {
			return subscriberId;
		}

		@Override
		public boolean isClosed() {
			return closed.get();
		}

		@Override
		public void close() {
			if (closed.compareAndSet(false, true)) {
				remove(this);
			}
		}

		@Override
		public String toString() {
			return subscriberId + " -> " + owner;
		}
	}
}

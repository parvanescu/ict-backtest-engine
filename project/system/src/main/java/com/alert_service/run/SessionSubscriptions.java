package com.alert_service.run;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Shares one resource per key between the client subscriptions that need it: opened by the first subscription,
 * closed when the last one leaves. A client subscription is identified by its STOMP session and subscription id.
 */
public final class SessionSubscriptions<R> {

	/** One STOMP subscription of one session. */
	public record Ref(String sessionId, String subscriptionId) {
	}

	private final Consumer<R> close;
	private final Map<String, Holders<R>> byKey = new HashMap<>();
	private final Map<Ref, String> keyByRef = new HashMap<>();

	public SessionSubscriptions(Consumer<R> close) {
		this.close = close;
	}

	/**
	 * Registers {@code ref} as a holder of {@code key}, opening the resource if it is the first. Idempotent per ref.
	 * If {@code open} throws, nothing is registered.
	 */
	public synchronized R acquire(Ref ref, String key, Supplier<R> open) {
		String held = keyByRef.get(ref);
		if (held != null && !held.equals(key)) {
			release(ref);
		}
		Holders<R> holders = byKey.get(key);
		if (holders == null) {
			holders = new Holders<>(open.get());
			byKey.put(key, holders);
		}
		holders.refs.add(ref);
		keyByRef.put(ref, key);
		return holders.resource;
	}

	/** @return whether {@code ref} held anything */
	public synchronized boolean release(Ref ref) {
		String key = keyByRef.remove(ref);
		if (key == null) {
			return false;
		}
		Holders<R> holders = byKey.get(key);
		holders.refs.remove(ref);
		if (holders.refs.isEmpty()) {
			byKey.remove(key);
			close.accept(holders.resource);
		}
		return true;
	}

	public synchronized void releaseSession(String sessionId) {
		List.copyOf(keyByRef.keySet()).stream().filter(ref -> ref.sessionId().equals(sessionId)).forEach(this::release);
	}

	public synchronized R resource(String key) {
		Holders<R> holders = byKey.get(key);
		return holders == null ? null : holders.resource;
	}

	/** Key to the sessions holding it. */
	public synchronized Map<String, Set<String>> holders() {
		Map<String, Set<String>> result = new HashMap<>();
		byKey.forEach((key, holders) -> {
			Set<String> sessions = new HashSet<>();
			holders.refs.forEach(ref -> sessions.add(ref.sessionId()));
			result.put(key, Set.copyOf(sessions));
		});
		return result;
	}

	private static final class Holders<R> {

		private final R resource;
		private final Set<Ref> refs = new HashSet<>();

		private Holders(R resource) {
			this.resource = resource;
		}
	}
}

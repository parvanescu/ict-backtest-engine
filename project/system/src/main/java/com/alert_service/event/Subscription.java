package com.alert_service.event;

/**
 * Handle for one listener on one source. Closing it is the only way to unsubscribe.
 */
public interface Subscription extends AutoCloseable {

	String id();

	String subscriberId();

	boolean isClosed();

	/** Idempotent. */
	@Override
	void close();
}

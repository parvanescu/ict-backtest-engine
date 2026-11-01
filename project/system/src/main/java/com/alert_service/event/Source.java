package com.alert_service.event;

/**
 * Something that emits events of type {@code E}. A source never knows what its listeners are.
 * Sources are hot: a listener only receives events emitted after it subscribed.
 */
public interface Source<E> {

	/**
	 * Subscribing twice with the same {@code subscriberId} returns the existing subscription.
	 */
	Subscription subscribe(String subscriberId, Listener<? super E> listener);
}

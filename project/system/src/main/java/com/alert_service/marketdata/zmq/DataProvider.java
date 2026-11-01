package com.alert_service.marketdata.zmq;

import com.alert_service.event.Source;
import com.alert_service.marketdata.Topic;

/**
 * Source of the decoded events of one topic. Knows nothing about rules.
 */
public interface DataProvider<E> extends Source<E> {

	Topic topic();

	boolean hasListeners();

	/** Stops receiving. Non-blocking and idempotent. */
	void close();
}

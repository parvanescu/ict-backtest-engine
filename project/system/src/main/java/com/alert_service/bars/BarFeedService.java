package com.alert_service.bars;

import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.alert_service.event.Subscription;
import com.alert_service.marketdata.DataRequirement;
import com.alert_service.marketdata.DataType;
import com.alert_service.marketdata.OhlcvBar;
import com.alert_service.marketdata.Topic;
import com.alert_service.marketdata.zmq.ProviderManager;
import com.alert_service.run.SessionSubscriptions;
import com.alert_service.run.SessionSubscriptions.Ref;

/**
 * Live bars for the users watching a symbol. One provider subscription per symbol, whatever the number of watchers,
 * taken through {@link ProviderManager} like any other consumer: it creates the provider or reuses one a rule
 * already opened. Each bar is published and kept in the {@link BarStore}. Runs on the provider's thread.
 */
@Service
public class BarFeedService {

	private static final Logger log = LoggerFactory.getLogger(BarFeedService.class);

	static final DataRequirement DATA = new DataRequirement(DataType.OHLCV);

	private final ProviderManager providers;
	private final BarStore store;
	private final BarPublisher publisher;
	private final SessionSubscriptions<Subscription> feeds = new SessionSubscriptions<>(Subscription::close);

	public BarFeedService(ProviderManager providers, BarStore store, BarPublisher publisher) {
		this.providers = providers;
		this.store = store;
		this.publisher = publisher;
	}

	/**
	 * @throws IllegalArgumentException for an invalid symbol
	 */
	public void watch(Ref ref, String symbol) {
		Topic topic = DATA.topicFor(symbol);
		feeds.acquire(ref, symbol,
				() -> providers.subscribe(topic, OhlcvBar.class, "BarFeed[" + symbol + "]", this::onBar));
	}

	/** @return whether {@code ref} was watching a symbol */
	public boolean unwatch(Ref ref) {
		return feeds.release(ref);
	}

	public void unwatchAll(String sessionId) {
		feeds.releaseSession(sessionId);
	}

	/** Symbol to the sessions watching it. */
	public Map<String, Set<String>> watchers() {
		return feeds.holders();
	}

	void onBar(OhlcvBar bar) {
		publisher.publish(bar.symbol(), BarMessage.bar(bar));
		try {
			store.append(bar);
		}
		catch (IllegalArgumentException ex) {
			log.warn("Bar of {} not stored: {}", bar.symbol(), ex.getMessage());
		}
	}
}

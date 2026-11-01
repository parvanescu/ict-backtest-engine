package com.alert_service.rule;

import java.time.LocalDate;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alert_service.event.Listener;

/**
 * A rule combining the facts of several basic rules and making every decision.
 * <p>
 * Basic rules call it from several provider threads, so their events go through its own queue and are processed
 * one at a time, in arrival order, on its own thread. {@link #onEvent} itself is synchronous.
 */
public abstract class AggregateRule<I, S extends RuleState> extends RuleInstance<I, RuleResult, S> {

	private static final Logger log = LoggerFactory.getLogger(AggregateRule.class);

	private final ExecutorService mailbox;
	private volatile DayStatus dayStatus = DayStatus.UNKNOWN;

	protected AggregateRule(RuleKey key) {
		super(key);
		this.mailbox = Executors.newSingleThreadExecutor(Thread.ofVirtual().name("rule-" + key).factory());
	}

	/** Queues an event for processing on the rule's thread. Events arriving after close are dropped. */
	public void enqueue(I event) {
		try {
			mailbox.execute(() -> {
				try {
					onEvent(event);
				}
				catch (RuntimeException ex) {
					log.error("Rule {} failed on {}", key(), event, ex);
				}
			});
		}
		catch (RejectedExecutionException closed) {
			log.debug("Rule {} is closed, dropped {}", key(), event);
		}
	}

	@Override
	public Listener<I> inputListener() {
		return this::enqueue;
	}

	@Override
	public final void onEvent(I event) {
		LocalDate day = tradingDayOf(event);
		if (state == null || !state.tradingDay().equals(day)) {
			state = newState(day);
			dayStatus = DayStatus.UNKNOWN;
		}
		process(event);
	}

	/** Status of the current trading day; readable from any thread. */
	public DayStatus dayStatus() {
		return dayStatus;
	}

	protected void dayStatus(DayStatus status) {
		this.dayStatus = status;
	}

	protected abstract LocalDate tradingDayOf(I event);

	protected abstract S newState(LocalDate tradingDay);

	/** Called for every event, after the state was reset if the event belongs to a new trading day. */
	protected abstract void process(I event);

	@Override
	protected void onClose() {
		mailbox.shutdown();
	}
}

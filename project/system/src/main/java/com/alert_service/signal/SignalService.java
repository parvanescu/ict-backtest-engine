package com.alert_service.signal;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.alert_service.rule.DayStatus;
import com.alert_service.rule.RuleKey;
import com.alert_service.rule.RuleRegistry;
import com.alert_service.rule.RuleRegistry.Attachment;
import com.alert_service.rule.RuleResult;
import com.alert_service.timegrid.TimeGrid;

/**
 * Listens to aggregate results and decides what reaches users. Holds one subscription per aggregate, whatever the
 * number of users, numbers its signals and keeps those of the current trading day for the history call.
 * Results are handled on the calling aggregate's thread.
 */
@Service
public class SignalService {

	static final String SUBSCRIBER_ID = "SignalService";

	private final RuleRegistry registry;
	private final SignalPublisher publisher;
	private final TimeGrid grid;
	private final Map<String, SignalStream> streams = new HashMap<>();

	public SignalService(RuleRegistry registry, SignalPublisher publisher, TimeGrid grid) {
		this.registry = registry;
		this.publisher = publisher;
		this.grid = grid;
	}

	/** Today's signals of a running rule. */
	public record SignalHistory(String ruleKey, DayStatus dayStatus, LocalDate tradingDay, List<Signal> signals) {
	}

	/** Starts publishing the results of the aggregate {@code key}, creating it if needed. Idempotent per key. */
	public synchronized SignalStream attach(RuleKey key) {
		SignalStream existing = streams.get(key.toString());
		if (existing != null) {
			return existing;
		}
		SignalStream stream = new SignalStream(key.toString());
		stream.attachment = registry.subscribe(key, SUBSCRIBER_ID, stream::onResult);
		streams.put(key.toString(), stream);
		return stream;
	}

	public synchronized Optional<SignalHistory> history(String ruleKey) {
		return Optional.ofNullable(streams.get(ruleKey)).map(SignalStream::history);
	}

	private synchronized void detach(SignalStream stream) {
		if (streams.remove(stream.ruleKey, stream)) {
			stream.attachment.subscription().close();
		}
	}

	/**
	 * The signals of one aggregate. Closing it stops publishing, drops the history and releases the aggregate.
	 */
	public final class SignalStream implements AutoCloseable {

		private final String ruleKey;
		private volatile Attachment attachment;
		private final List<Signal> today = new ArrayList<>();
		private LocalDate tradingDay;
		private long nextId = 1;

		private SignalStream(String ruleKey) {
			this.ruleKey = ruleKey;
		}

		public DayStatus dayStatus() {
			return attachment.rule().dayStatus();
		}

		@Override
		public void close() {
			detach(this);
		}

		/** Iteration 1: every result kind is forwarded. Filtering or combining results belongs here. */
		private void onResult(RuleResult result) {
			publisher.publish(ruleKey, record(result));
		}

		private synchronized Signal record(RuleResult result) {
			LocalDate day = grid.position(result.eventTime()).tradingDay();
			if (!day.equals(tradingDay)) {
				tradingDay = day;
				today.clear();
			}
			Signal signal = Signal.of(nextId++, result);
			today.add(signal);
			return signal;
		}

		private synchronized SignalHistory history() {
			return new SignalHistory(ruleKey, dayStatus(), tradingDay, List.copyOf(today));
		}
	}
}

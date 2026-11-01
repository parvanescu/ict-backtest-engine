package com.alert_service.rule;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.alert_service.event.Listener;
import com.alert_service.event.ListenerList;
import com.alert_service.event.Source;
import com.alert_service.event.Subscription;

/**
 * A running rule: its {@link RuleState} plus the logic that updates it from input events of type {@code I}
 * and emits events of type {@code O} to its listeners.
 */
public abstract class RuleInstance<I, O, S extends RuleState> implements Source<O> {

	private final RuleKey key;
	private final ListenerList<O> listeners;
	private final List<Subscription> inputs = new CopyOnWriteArrayList<>();
	private volatile Runnable whenUnused = () -> {
	};

	protected S state;

	protected RuleInstance(RuleKey key) {
		this.key = key;
		this.listeners = new ListenerList<>(key.toString(), () -> whenUnused.run());
	}

	public RuleKey key() {
		return key;
	}

	@Override
	public Subscription subscribe(String subscriberId, Listener<? super O> listener) {
		return listeners.subscribe(subscriberId, listener);
	}

	public boolean hasListeners() {
		return !listeners.isEmpty();
	}

	/** Processes one input event, synchronously, on the calling thread. */
	public abstract void onEvent(I event);

	/** What this instance subscribes to its inputs with. */
	public abstract Listener<I> inputListener();

	protected void emit(O event) {
		listeners.dispatch(event);
	}

	void addInput(Subscription input) {
		inputs.add(input);
	}

	void whenUnused(Runnable action) {
		this.whenUnused = action;
	}

	/** Closes the input subscriptions. Called by the registry once the instance has no listeners left. */
	void close() {
		inputs.forEach(Subscription::close);
		inputs.clear();
		onClose();
	}

	protected void onClose() {
	}
}

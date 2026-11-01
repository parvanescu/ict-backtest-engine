package com.alert_service.rule;

import java.time.LocalDate;

import com.alert_service.event.Listener;
import com.alert_service.marketdata.OhlcvBar;
import com.alert_service.timegrid.GridPosition;
import com.alert_service.timegrid.TimeGrid;

/**
 * A rule over one instrument. It listens to one provider, runs on that provider's thread, and reports facts.
 * It decides nothing and knows nothing about who listens.
 * <p>
 * The late start check is done here: the first bar of a trading day must start before the descriptor's
 * earliest block, otherwise the day is invalid for the rule ({@link #dayValid()}).
 */
public abstract class BasicRule<O, S extends RuleState> extends RuleInstance<OhlcvBar, O, S> {

	private final BasicRuleDescriptor descriptor;
	private final String symbol;
	private final TimeGrid grid;
	private boolean dayValid;

	protected BasicRule(BasicRuleDescriptor descriptor, RuleKey key, String symbol, TimeGrid grid) {
		super(key);
		this.descriptor = descriptor;
		this.symbol = symbol;
		this.grid = grid;
	}

	public String symbol() {
		return symbol;
	}

	@Override
	public final void onEvent(OhlcvBar bar) {
		GridPosition position = grid.positionOfEpochNanos(bar.startTime());
		if (state == null || !state.tradingDay().equals(position.tradingDay())) {
			dayValid = bar.startInstant()
				.isBefore(position.blockStart(descriptor.earliestLevel(), descriptor.earliestBlock()));
			state = newState(position.tradingDay());
		}
		onBar(bar, position);
	}

	@Override
	public Listener<OhlcvBar> inputListener() {
		return this::onEvent;
	}

	/** Whether the rule started early enough today. */
	protected boolean dayValid() {
		return dayValid;
	}

	protected abstract S newState(LocalDate tradingDay);

	/** Called for every bar, after the state was reset if the bar opened a new trading day. */
	protected abstract void onBar(OhlcvBar bar, GridPosition position);
}

package com.alert_service.rule.basic.rangebreak;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.alert_service.marketdata.DataRequirement;
import com.alert_service.marketdata.DataType;
import com.alert_service.marketdata.OhlcvBar;
import com.alert_service.rule.BasicRule;
import com.alert_service.rule.BasicRuleDescriptor;
import com.alert_service.rule.RuleKey;
import com.alert_service.rule.basic.rangebreak.RangeBreakEvent.BarPositioned;
import com.alert_service.rule.basic.rangebreak.RangeBreakEvent.RangeCaptured;
import com.alert_service.rule.basic.rangebreak.RangeBreakEvent.RangeUnavailable;
import com.alert_service.rule.basic.rangebreak.RangeBreakEvent.RangeUnavailable.Reason;
import com.alert_service.rule.basic.rangebreak.RangeBreakEvent.WindowClosed;
import com.alert_service.rule.basic.rangebreak.RangeBreakWatchState.Phase;
import com.alert_service.timegrid.GridLevel;
import com.alert_service.timegrid.GridPosition;
import com.alert_service.timegrid.TimeGrid;

/**
 * For one instrument: captures the high, low and mean of the range block (3-3-3, L3 block 42) and reports the
 * position of every bar of the watch block (3-3-4, L3 block 43) against them.
 */
public final class RangeBreakWatch extends BasicRule<RangeBreakEvent, RangeBreakWatchState> {

	public static final GridLevel LEVEL = GridLevel.L3_22_5M;
	public static final int RANGE_BLOCK = 42;
	public static final int WATCH_BLOCK = RANGE_BLOCK + 1;

	public static final BasicRuleDescriptor DESCRIPTOR = new BasicRuleDescriptor("RANGE_BREAK_WATCH",
			new DataRequirement(DataType.OHLCV), RangeBreakEvent.class, LEVEL, RANGE_BLOCK);

	private static final BigDecimal TWO = BigDecimal.valueOf(2);

	public RangeBreakWatch(RuleKey key, String symbol, TimeGrid grid) {
		super(DESCRIPTOR, key, symbol, grid);
	}

	@Override
	protected RangeBreakWatchState newState(LocalDate tradingDay) {
		return new RangeBreakWatchState(tradingDay);
	}

	@Override
	protected void onBar(OhlcvBar bar, GridPosition position) {
		int block = position.block(LEVEL);
		if (state.phase == Phase.DONE || block < RANGE_BLOCK) {
			return;
		}
		if (!dayValid()) {
			if (block >= WATCH_BLOCK) {
				unavailable(bar, Reason.LATE_START);
			}
			return;
		}
		if (block == RANGE_BLOCK) {
			capture(bar);
			return;
		}
		if (state.phase != Phase.WATCHING && !freeze(bar)) {
			return;
		}
		if (block == WATCH_BLOCK) {
			position(bar);
		}
		else {
			emit(new WindowClosed(symbol(), state.tradingDay, bar.startInstant()));
			state.phase = Phase.DONE;
		}
	}

	private void capture(OhlcvBar bar) {
		state.runningHigh = state.runningHigh == null ? bar.high() : state.runningHigh.max(bar.high());
		state.runningLow = state.runningLow == null ? bar.low() : state.runningLow.min(bar.low());
		state.phase = Phase.CAPTURING;
	}

	/** @return whether a range was captured */
	private boolean freeze(OhlcvBar bar) {
		if (state.runningHigh == null) {
			unavailable(bar, Reason.NO_BARS_IN_RANGE);
			return false;
		}
		state.high = state.runningHigh;
		state.low = state.runningLow;
		state.mean = state.high.add(state.low).divide(TWO);
		state.phase = Phase.WATCHING;
		emit(new RangeCaptured(symbol(), state.tradingDay, bar.startInstant(), state.high, state.low, state.mean));
		return true;
	}

	private void position(OhlcvBar bar) {
		emit(new BarPositioned(symbol(), state.tradingDay, bar.startInstant(), bar.high(), bar.low(),
				bar.high().compareTo(state.high) > 0, bar.low().compareTo(state.low) < 0));
	}

	private void unavailable(OhlcvBar bar, Reason reason) {
		emit(new RangeUnavailable(symbol(), state.tradingDay, bar.startInstant(), reason));
		state.phase = Phase.DONE;
	}
}

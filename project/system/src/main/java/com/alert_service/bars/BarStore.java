package com.alert_service.bars;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.alert_service.marketdata.OhlcvBar;
import com.alert_service.timegrid.TimeGrid;

/**
 * The 1-second bars of the current trading day per symbol, for charts opened during the day. Only bars received
 * while a bar feed was open are kept: the service never fetches history.
 * <p>
 * Stored as primitive arrays (48 bytes per bar, about 4 MB per symbol for a full day). Prices are kept as whole
 * numbers scaled by 10^6; a price with more decimals is not stored. A symbol's bars are dropped when its first bar of
 * a new trading day arrives.
 */
@Component
public class BarStore {

	private static final Logger log = LoggerFactory.getLogger(BarStore.class);

	static final int PRICE_SCALE = 6;
	static final int MAX_BARS_PER_DAY = 86_400;
	private static final int INITIAL_CAPACITY = 4_096;

	private final TimeGrid grid;
	private final Map<String, DayBars> bySymbol = new ConcurrentHashMap<>();

	public BarStore(TimeGrid grid) {
		this.grid = grid;
	}

	/** A symbol's stored bars from {@code from} on. {@code tradingDay} is null if nothing was stored. */
	public record Snapshot(String symbol, LocalDate tradingDay, List<OhlcvBar> bars) {
	}

	/**
	 * @throws IllegalArgumentException if the bar falls in no trading day
	 */
	public void append(OhlcvBar bar) {
		LocalDate day = grid.positionOfEpochNanos(bar.startTime()).tradingDay();
		bySymbol.computeIfAbsent(bar.symbol(), DayBars::new).append(bar, day);
	}

	public Snapshot since(String symbol, Instant from) {
		DayBars bars = bySymbol.get(symbol);
		if (bars == null) {
			return new Snapshot(symbol, null, List.of());
		}
		return bars.since(from == null ? Long.MIN_VALUE : toNanos(from));
	}

	private static long toNanos(Instant instant) {
		return Math.addExact(Math.multiplyExact(instant.getEpochSecond(), 1_000_000_000L), instant.getNano());
	}

	static long scaled(BigDecimal price) {
		return price.setScale(PRICE_SCALE, RoundingMode.UNNECESSARY).unscaledValue().longValueExact();
	}

	static BigDecimal unscaled(long value) {
		BigDecimal price = BigDecimal.valueOf(value, PRICE_SCALE).stripTrailingZeros();
		return price.scale() < 0 ? price.setScale(0) : price;
	}

	/** One symbol's bars of one trading day, as parallel arrays ordered by start time. */
	private static final class DayBars {

		private final String symbol;
		private LocalDate tradingDay;
		private long[] start = new long[INITIAL_CAPACITY];
		private long[] open = new long[INITIAL_CAPACITY];
		private long[] high = new long[INITIAL_CAPACITY];
		private long[] low = new long[INITIAL_CAPACITY];
		private long[] close = new long[INITIAL_CAPACITY];
		private long[] volume = new long[INITIAL_CAPACITY];
		private int size;

		private DayBars(String symbol) {
			this.symbol = symbol;
		}

		synchronized void append(OhlcvBar bar, LocalDate day) {
			if (!day.equals(tradingDay)) {
				tradingDay = day;
				size = 0;
			}
			if (size > 0 && bar.startTime() <= start[size - 1]) {
				return;
			}
			if (size == MAX_BARS_PER_DAY) {
				log.warn("Bar store for {} is full for {}, dropped bar at {}", symbol, day, bar.startInstant());
				return;
			}
			long o;
			long h;
			long l;
			long c;
			try {
				o = scaled(bar.open());
				h = scaled(bar.high());
				l = scaled(bar.low());
				c = scaled(bar.close());
			}
			catch (ArithmeticException ex) {
				log.warn("Bar of {} at {} has a price with more than {} decimals; not stored", symbol,
						bar.startInstant(), PRICE_SCALE);
				return;
			}
			if (size == start.length) {
				grow();
			}
			start[size] = bar.startTime();
			open[size] = o;
			high[size] = h;
			low[size] = l;
			close[size] = c;
			volume[size] = bar.volume();
			size++;
		}

		synchronized Snapshot since(long fromNanos) {
			int first = Arrays.binarySearch(start, 0, size, fromNanos);
			if (first < 0) {
				first = -first - 1;
			}
			List<OhlcvBar> bars = new ArrayList<>(size - first);
			for (int i = first; i < size; i++) {
				bars.add(new OhlcvBar(symbol, start[i], unscaled(open[i]), unscaled(high[i]), unscaled(low[i]),
						unscaled(close[i]), volume[i]));
			}
			return new Snapshot(symbol, tradingDay, bars);
		}

		private void grow() {
			int capacity = Math.min(start.length * 2, MAX_BARS_PER_DAY);
			start = Arrays.copyOf(start, capacity);
			open = Arrays.copyOf(open, capacity);
			high = Arrays.copyOf(high, capacity);
			low = Arrays.copyOf(low, capacity);
			close = Arrays.copyOf(close, capacity);
			volume = Arrays.copyOf(volume, capacity);
		}
	}
}

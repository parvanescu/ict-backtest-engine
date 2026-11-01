package com.alert_service.demo;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.alert_service.demo.Scenario.Shape;
import com.alert_service.demo.Scenario.Trigger;
import com.alert_service.marketdata.OhlcvBar;
import com.alert_service.timegrid.TimeGrid;

/**
 * Generates a scenario's 1-second bars of ES, NQ, YM and RTY, deterministically (fixed seed), for 2026-10-12 from the
 * start of 3-3-2 (09:22:30 New York) to the end of 3-4-1 (10:52:30), the span in which the Sequence range sweep can
 * trigger. 3-3-3 (09:45:00-10:07:30) builds each instrument's range; during 3-3-4 (10:07:30-10:30:00) prices stay
 * inside their ranges except at the scenario's scripted seconds; before and after they walk freely.
 */
public final class ScenarioPlayer {

	static final LocalDate DAY = LocalDate.of(2026, 10, 12);
	static final LocalTime WINDOW_START = LocalTime.of(9, 22, 30);
	static final LocalTime WINDOW_END = LocalTime.of(10, 52, 30);
	private static final LocalTime RANGE_START = LocalTime.of(9, 45);
	private static final LocalTime WATCH_START = LocalTime.of(10, 7, 30);
	private static final LocalTime WATCH_END = LocalTime.of(10, 30);

	public static final List<String> SYMBOLS = List.of("ES", "NQ", "YM", "RTY");

	/** Receives each generated bar, in time order, instruments interleaved. */
	public interface BarSink {
		void accept(OhlcvBar bar) throws InterruptedException;
	}

	/** Told before each simulated second: its New York time and the share of the window played so far. */
	public interface Progress {
		void at(LocalTime newYorkTime, int percent);
	}

	private record Instrument(String symbol, BigDecimal tick, long startTicks, int volatilityTicks) {
	}

	private static final List<Instrument> INSTRUMENTS = List.of(
			new Instrument("ES", new BigDecimal("0.25"), 24_120, 2),
			new Instrument("NQ", new BigDecimal("0.25"), 84_400, 6),
			new Instrument("YM", new BigDecimal("1"), 44_200, 6),
			new Instrument("RTY", new BigDecimal("0.10"), 21_800, 3));

	/** Walk state and 3-3-3 range of one instrument, in ticks. */
	private static final class Walk {

		final Instrument instrument;
		long price;
		long rangeHigh = Long.MIN_VALUE;
		long rangeLow = Long.MAX_VALUE;

		Walk(Instrument instrument) {
			this.instrument = instrument;
			this.price = instrument.startTicks();
		}

		long mean() {
			return (rangeHigh + rangeLow) / 2;
		}
	}

	private ScenarioPlayer() {
	}

	public static Instant windowStart() {
		return at(WINDOW_START);
	}

	public static Instant windowEnd() {
		return at(WINDOW_END);
	}

	/** Generates every bar of {@code scenario}, handing each to {@code sink}. */
	public static void play(Scenario scenario, BarSink sink) throws InterruptedException {
		play(scenario, sink, (time, percent) -> {
		});
	}

	/** Like {@link #play(Scenario, BarSink)}, also reporting the progress. */
	public static void play(Scenario scenario, BarSink sink, Progress progress) throws InterruptedException {
		Random random = new Random(42);
		Map<String, Walk> walks = new LinkedHashMap<>();
		INSTRUMENTS.forEach(instrument -> walks.put(instrument.symbol(), new Walk(instrument)));
		Map<LocalTime, Trigger> triggers = scenario.triggers()
			.stream()
			.collect(Collectors.toMap(Trigger::time, Function.identity()));

		Instant start = windowStart();
		long seconds = windowEnd().getEpochSecond() - start.getEpochSecond();
		for (long s = 0; s < seconds; s++) {
			Instant time = start.plusSeconds(s);
			LocalTime ny = time.atZone(TimeGrid.NEW_YORK).toLocalTime();
			progress.at(ny, (int) (100 * s / seconds));
			Trigger trigger = triggers.get(ny);
			double market = random.nextGaussian();
			for (Walk walk : walks.values()) {
				Shape shape = trigger == null ? null : trigger.shapes().get(walk.instrument.symbol());
				long[] bar = shape == null ? randomBar(walk, market, random, ny) : scriptedBar(walk, shape);
				if (!ny.isBefore(RANGE_START) && ny.isBefore(WATCH_START)) {
					walk.rangeHigh = Math.max(walk.rangeHigh, bar[1]);
					walk.rangeLow = Math.min(walk.rangeLow, bar[2]);
				}
				sink.accept(toBar(walk.instrument, time, bar));
			}
		}
	}

	/** {open, high, low, close} in ticks, from four correlated random steps. */
	private static long[] randomBar(Walk walk, double market, Random random, LocalTime ny) {
		long open = walk.price;
		long high = open;
		long low = open;
		long price = open;
		for (int step = 0; step < 4; step++) {
			price += Math.round((0.6 * market + 0.8 * random.nextGaussian()) * walk.instrument.volatilityTicks() / 2);
			high = Math.max(high, price);
			low = Math.min(low, price);
		}
		// During 3-3-4 only the scripted seconds break a range.
		if (!ny.isBefore(WATCH_START) && ny.isBefore(WATCH_END)) {
			long top = walk.rangeHigh - 1;
			long bottom = walk.rangeLow + 1;
			open = clamp(open, bottom, top);
			price = clamp(price, bottom, top);
			high = clamp(high, bottom, top);
			low = clamp(low, bottom, top);
		}
		walk.price = price;
		return new long[] { open, Math.max(high, Math.max(open, price)), Math.min(low, Math.min(open, price)), price };
	}

	/** {open, high, low, close} in ticks, placed against the instrument's 3-3-3 range as {@code shape} says. */
	private static long[] scriptedBar(Walk walk, Shape shape) {
		long high = walk.rangeHigh;
		long low = walk.rangeLow;
		long mean = walk.mean();
		long beyond = Math.max(2, (high - low) / 10);
		// {low, high, close}
		long[] lowHighClose = switch (shape) {
			case SWEEP_LOW -> new long[] { low - beyond, mean, mean };
			case SWEEP_HIGH -> new long[] { mean, high + beyond, mean };
			case LOWER_HALF -> {
				long barLow = low + Math.max(1, (mean - low) / 3);
				yield new long[] { barLow, barLow + 1, barLow + 1 };
			}
			case UPPER_HALF -> {
				long barHigh = high - Math.max(1, (high - mean) / 3);
				yield new long[] { barHigh - 1, barHigh, barHigh - 1 };
			}
		};
		long open = clamp(walk.price, lowHighClose[0], lowHighClose[1]);
		walk.price = lowHighClose[2];
		return new long[] { open, lowHighClose[1], lowHighClose[0], lowHighClose[2] };
	}

	private static OhlcvBar toBar(Instrument instrument, Instant time, long[] bar) {
		return new OhlcvBar(instrument.symbol(), time.getEpochSecond() * 1_000_000_000L, price(instrument, bar[0]),
				price(instrument, bar[1]), price(instrument, bar[2]), price(instrument, bar[3]),
				1 + Math.abs(bar[3] - bar[0]));
	}

	private static BigDecimal price(Instrument instrument, long ticks) {
		return instrument.tick().multiply(BigDecimal.valueOf(ticks));
	}

	private static long clamp(long value, long min, long max) {
		return Math.max(min, Math.min(max, value));
	}

	private static Instant at(LocalTime nyTime) {
		return DAY.atTime(nyTime).atZone(TimeGrid.NEW_YORK).toInstant();
	}
}

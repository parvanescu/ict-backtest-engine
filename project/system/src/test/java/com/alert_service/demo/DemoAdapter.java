package com.alert_service.demo;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import com.alert_service.timegrid.TimeGrid;

/**
 * A stand-in market data adapter for trying the UI: plays a synthetic trading day of ES, NQ, YM and RTY 1-second bars
 * on a ZeroMQ PUB socket, faster than real time.
 * <p>
 * It waits until the service subscribes to all four symbols (start the sequence range sweep in the UI), then plays
 * 2026-10-12 from 09:44 New York, just before the 3-3-3 range opens, to the 17:00 close. Prices are random walks,
 * kept inside each 3-3-3 range during 3-3-4 except at the scripted {@link #EVENTS}, which trigger the rule:
 * <ul>
 * <li>10:12:40: YM sweeps its low; ES and RTY are in the lower half of their ranges: range sweep events for ES and RTY.</li>
 * <li>10:20:15: NQ sweeps its high; YM is in the upper half of its range: a range sweep event for YM.</li>
 * </ul>
 * Bars carry the simulated times, so the pace changes nothing in the results. After the close it stays idle. The
 * service keeps the day's bars and rule state, so playing the day again needs a restart of the service and of this
 * adapter.
 * <p>
 * Run with {@code ./gradlew demoAdapter}. Options ({@code --args="..."}): {@code --endpoint} (default
 * {@code tcp://127.0.0.1:5556}; {@code tcp://*:5556} to accept other hosts), {@code --speed} (simulated seconds per
 * real second, default 60), {@code --start-delay} (seconds to wait after the subscriptions, default 3).
 */
public final class DemoAdapter {

	private static final LocalDate DAY = LocalDate.of(2026, 10, 12);
	private static final LocalTime START = LocalTime.of(9, 44);
	private static final LocalTime RANGE_START = LocalTime.of(9, 45);
	private static final LocalTime WATCH_START = LocalTime.of(10, 7, 30);
	private static final LocalTime WATCH_END = LocalTime.of(10, 30);
	private static final LocalTime END = LocalTime.of(17, 0);

	private record Instrument(String symbol, BigDecimal tick, long startTicks, int volatilityTicks) {
	}

	private static final Instrument[] INSTRUMENTS = {
			new Instrument("ES", new BigDecimal("0.25"), 24_120, 2),
			new Instrument("NQ", new BigDecimal("0.25"), 84_400, 6),
			new Instrument("YM", new BigDecimal("1"), 44_200, 6),
			new Instrument("RTY", new BigDecimal("0.10"), 21_800, 3) };

	/** How an instrument's bar is placed against its own 3-3-3 range at a scripted second. */
	private enum Shape {
		/** Wick below the range low, close back at the mean. */
		SWEEP_LOW,
		/** Wick above the range high, close back at the mean. */
		SWEEP_HIGH,
		/** Whole bar in the lower half, low between L and M. */
		LOWER_HALF,
		/** Whole bar in the upper half, high between M and H. */
		UPPER_HALF
	}

	private record Event(LocalTime time, Map<String, Shape> shapes, String expect) {
	}

	private static final List<Event> EVENTS = List.of(
			new Event(LocalTime.of(10, 12, 40),
					Map.of("YM", Shape.SWEEP_LOW, "ES", Shape.LOWER_HALF, "RTY", Shape.LOWER_HALF, "NQ",
							Shape.UPPER_HALF),
					"YM sweeps its 3-3-3 low; ES and RTY are in their lower half: expect range sweep events for ES and RTY"),
			new Event(LocalTime.of(10, 20, 15),
					Map.of("NQ", Shape.SWEEP_HIGH, "YM", Shape.UPPER_HALF, "ES", Shape.LOWER_HALF, "RTY",
							Shape.LOWER_HALF),
					"NQ sweeps its 3-3-3 high; YM is in its upper half: expect a range sweep event for YM"
							+ " (ES and RTY already have today's event)"));

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

	private DemoAdapter() {
	}

	public static void main(String[] args) throws InterruptedException {
		String endpoint = option(args, "--endpoint", "tcp://127.0.0.1:5556");
		int speed = Integer.parseInt(option(args, "--speed", "60"));
		int startDelay = Integer.parseInt(option(args, "--start-delay", "3"));
		try (ZContext context = new ZContext()) {
			ZMQ.Socket socket = context.createSocket(SocketType.XPUB);
			socket.bind(endpoint);
			System.out.println("Demo adapter bound on " + endpoint);
			awaitSubscriptions(socket);
			// The charts subscribe to the bars as soon as the UI opens; give the rule a moment to subscribe too.
			System.out.printf("Starting in %d s%n", startDelay);
			Thread.sleep(startDelay * 1000L);
			play(socket, speed);
			System.out.println("Done. Idle now; to play the day again restart the service and this adapter.");
			Thread.currentThread().join();
		}
	}

	private static void awaitSubscriptions(ZMQ.Socket socket) {
		Set<String> wanted = new HashSet<>();
		for (Instrument instrument : INSTRUMENTS) {
			wanted.add("OHLCV." + instrument.symbol());
		}
		System.out.println("Waiting for the service to subscribe to " + wanted
				+ ": open the UI (the sequence range sweep on ES, NQ, YM, RTY runs by default)");
		Set<String> seen = new HashSet<>();
		while (!seen.containsAll(wanted)) {
			byte[] frame = socket.recv();
			if (frame != null && frame.length > 1 && frame[0] == 1) {
				String topic = new String(frame, 1, frame.length - 1, StandardCharsets.UTF_8);
				seen.add(topic);
				System.out.println("  subscribed: " + topic);
			}
		}
	}

	private static void play(ZMQ.Socket socket, int speed) throws InterruptedException {
		Random random = new Random(42);
		Map<String, Walk> walks = new LinkedHashMap<>();
		for (Instrument instrument : INSTRUMENTS) {
			walks.put(instrument.symbol(), new Walk(instrument));
		}
		Map<LocalTime, Event> events = new LinkedHashMap<>();
		EVENTS.forEach(event -> events.put(event.time(), event));

		Instant start = at(START);
		long seconds = at(END).getEpochSecond() - start.getEpochSecond();
		System.out.printf("Playing %s %s-%s New York: %d bars per instrument at %dx (about %d min)%n", DAY, START, END,
				seconds, speed, Math.round(seconds / (double) speed / 60));
		for (long s = 0; s < seconds; s++) {
			Instant time = start.plusSeconds(s);
			LocalTime ny = time.atZone(TimeGrid.NEW_YORK).toLocalTime();
			Event event = events.get(ny);
			double market = random.nextGaussian();
			for (Walk walk : walks.values()) {
				Shape shape = event == null ? null : event.shapes().get(walk.instrument.symbol());
				long[] bar = shape == null ? randomBar(walk, market, random, ny) : scriptedBar(walk, shape);
				if (!ny.isBefore(RANGE_START) && ny.isBefore(WATCH_START)) {
					walk.rangeHigh = Math.max(walk.rangeHigh, bar[1]);
					walk.rangeLow = Math.min(walk.rangeLow, bar[2]);
				}
				publish(socket, walk.instrument, time, bar);
			}
			if (event != null) {
				System.out.println(ny + " NY: " + event.expect());
			}
			if (ny.equals(WATCH_END)) {
				System.out.println("10:30 NY: 3-3-4 is over, prices now walk freely until the 17:00 close");
			}
			Thread.sleep(1000L / speed);
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
		// During 3-3-4 only the scripted events break a range, so the demo shows exactly them.
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

	private static void publish(ZMQ.Socket socket, Instrument instrument, Instant time, long[] bar) {
		long nanos = time.getEpochSecond() * 1_000_000_000L;
		String json = "{\"symbol\":\"%s\",\"startTime\":%d,\"open\":%s,\"high\":%s,\"low\":%s,\"close\":%s,\"volume\":%d}"
			.formatted(instrument.symbol(), nanos, price(instrument, bar[0]), price(instrument, bar[1]),
					price(instrument, bar[2]), price(instrument, bar[3]), 1 + Math.abs(bar[3] - bar[0]));
		socket.sendMore("OHLCV." + instrument.symbol());
		socket.send(json);
	}

	private static String price(Instrument instrument, long ticks) {
		return instrument.tick().multiply(BigDecimal.valueOf(ticks)).toPlainString();
	}

	private static long clamp(long value, long min, long max) {
		return Math.max(min, Math.min(max, value));
	}

	private static Instant at(LocalTime nyTime) {
		return DAY.atTime(nyTime).atZone(TimeGrid.NEW_YORK).toInstant();
	}

	private static String option(String[] args, String name, String fallback) {
		for (int i = 0; i < args.length - 1; i++) {
			if (args[i].equals(name)) {
				return args[i + 1];
			}
		}
		return fallback;
	}
}

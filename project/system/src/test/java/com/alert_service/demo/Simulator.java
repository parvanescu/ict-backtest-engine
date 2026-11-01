package com.alert_service.demo;

import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import com.alert_service.marketdata.OhlcvBar;

/**
 * Plays {@link Scenario scenarios} on a market data wire, one at a time, at a chosen speed. One thread owns the wire
 * (ZeroMQ sockets are not thread-safe): while idle it follows which topics the service subscribed to, while playing
 * it sends the bars. Playing needs the service subscribed to every simulated symbol, since a late subscriber would
 * miss bars.
 */
public final class Simulator implements AutoCloseable {

	/** The ZeroMQ side, called from the simulator thread only. */
	public interface Wire {

		/** Replaces {@code topics} with the topics the service is subscribed to now. */
		void drainSubscriptions(Set<String> topics);

		void send(OhlcvBar bar);
	}

	/**
	 * @param state         {@code idle}, {@code playing} or {@code done}
	 * @param scenario      id of the running or last scenario, or null
	 * @param simulatedTime New York time of the second being played, or null
	 * @param percent       share of the window played
	 * @param subscribed    simulated symbols the service is subscribed to
	 * @param missing       simulated symbols the service is not subscribed to yet
	 */
	public record Status(String state, String scenario, String simulatedTime, int percent, Set<String> subscribed,
			Set<String> missing) {
	}

	private static final String TOPIC_PREFIX = "OHLCV.";

	private final Wire wire;
	private final Set<String> topics = ConcurrentHashMap.newKeySet();
	private final BlockingQueue<Runnable> commands = new LinkedBlockingQueue<>();
	private final Thread thread;

	private volatile boolean closed;
	private volatile boolean stopRequested;
	private volatile boolean busy;
	private volatile String state = "idle";
	private volatile String scenarioId;
	private volatile String simulatedTime;
	private volatile int percent;
	private volatile int speed;

	public Simulator(Wire wire) {
		this.wire = wire;
		this.thread = Thread.ofPlatform().name("simulator").daemon().start(this::run);
	}

	/**
	 * Starts a scenario.
	 *
	 * @param speed simulated seconds per real second
	 * @throws IllegalArgumentException for an unknown scenario or a speed below 1
	 * @throws IllegalStateException    if a scenario is playing or the service is not subscribed to every symbol
	 */
	public synchronized void play(String scenario, int speed) {
		Scenario chosen = Scenarios.byId(scenario)
			.orElseThrow(() -> new IllegalArgumentException("Unknown scenario '" + scenario + "'"));
		if (speed < 1) {
			throw new IllegalArgumentException("Speed must be at least 1 simulated second per second");
		}
		if (busy) {
			throw new IllegalStateException("Scenario " + scenarioId + " is already playing; stop it first");
		}
		Set<String> missing = missing();
		if (!missing.isEmpty()) {
			throw new IllegalStateException("Waiting for the service to subscribe to " + missing
					+ ": open the UI and keep the rule running");
		}
		busy = true;
		stopRequested = false;
		this.speed = speed;
		scenarioId = chosen.id();
		simulatedTime = null;
		percent = 0;
		state = "playing";
		commands.add(() -> playNow(chosen));
	}

	/** Ends the running scenario; does nothing when none runs. */
	public void stop() {
		stopRequested = true;
	}

	public Status status() {
		Set<String> missing = missing();
		Set<String> subscribed = new TreeSet<>(ScenarioPlayer.SYMBOLS);
		subscribed.removeAll(missing);
		return new Status(state, scenarioId, simulatedTime, percent, subscribed, missing);
	}

	@Override
	public void close() {
		closed = true;
		stopRequested = true;
		thread.interrupt();
	}

	private Set<String> missing() {
		Set<String> missing = new TreeSet<>();
		for (String symbol : ScenarioPlayer.SYMBOLS) {
			if (!topics.contains(TOPIC_PREFIX + symbol)) {
				missing.add(symbol);
			}
		}
		return missing;
	}

	private void run() {
		try {
			while (!closed) {
				followSubscriptions();
				Runnable command = commands.poll(100, TimeUnit.MILLISECONDS);
				if (command != null) {
					command.run();
				}
			}
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

	private void followSubscriptions() {
		Set<String> now = new HashSet<>();
		wire.drainSubscriptions(now);
		topics.retainAll(now);
		topics.addAll(now);
	}

	private void playNow(Scenario scenario) {
		long nanosPerSecond = 1_000_000_000L / speed;
		try {
			ScenarioPlayer.play(scenario, wire::send, (time, percentPlayed) -> {
				if (stopRequested) {
					throw new CancellationException();
				}
				simulatedTime = time.toString();
				percent = percentPlayed;
				followSubscriptions();
				try {
					TimeUnit.NANOSECONDS.sleep(nanosPerSecond);
				}
				catch (InterruptedException ex) {
					Thread.currentThread().interrupt();
					throw new CancellationException();
				}
			});
			percent = 100;
			state = "done";
		}
		catch (CancellationException | InterruptedException ex) {
			state = "idle";
		}
		finally {
			busy = false;
		}
	}
}

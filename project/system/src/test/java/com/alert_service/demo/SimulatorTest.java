package com.alert_service.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.alert_service.marketdata.OhlcvBar;

class SimulatorTest {

	/** A wire with no ZeroMQ: the test decides which topics are subscribed. */
	static final class FakeWire implements Simulator.Wire {

		final Set<String> subscribed = java.util.concurrent.ConcurrentHashMap.newKeySet();
		final CopyOnWriteArrayList<OhlcvBar> sent = new CopyOnWriteArrayList<>();
		final AtomicInteger count = new AtomicInteger();

		@Override
		public void drainSubscriptions(Set<String> topics) {
			topics.clear();
			topics.addAll(subscribed);
		}

		@Override
		public void send(OhlcvBar bar) {
			count.incrementAndGet();
		}

		void subscribeAll() {
			ScenarioPlayer.SYMBOLS.forEach(symbol -> subscribed.add("OHLCV." + symbol));
		}
	}

	private final FakeWire wire = new FakeWire();
	private final Simulator simulator = new Simulator(wire);

	@AfterEach
	void close() {
		simulator.close();
	}

	@Test
	void refusesToPlayUntilTheServiceSubscribedToAllFourSymbols() {
		wire.subscribed.add("OHLCV.ES");
		await().until(() -> simulator.status().subscribed().contains("ES"));

		assertThatThrownBy(() -> simulator.play("low-sweep", 60)).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("NQ")
			.hasMessageContaining("YM")
			.hasMessageContaining("RTY");
		assertThat(simulator.status().state()).isEqualTo("idle");
	}

	@Test
	void playsAScenarioToTheEndAndSendsEveryBar() {
		wire.subscribeAll();
		await().until(() -> simulator.status().missing().isEmpty());

		simulator.play("low-sweep", 100_000);

		await().until(() -> simulator.status().state().equals("done"));
		assertThat(wire.count.get()).isEqualTo(4 * 5_400);
		assertThat(simulator.status().percent()).isEqualTo(100);
	}

	@Test
	void refusesASecondPlayWhileOneIsRunningAndStopEndsIt() {
		wire.subscribeAll();
		await().until(() -> simulator.status().missing().isEmpty());
		simulator.play("low-sweep", 1);
		await().until(() -> simulator.status().state().equals("playing"));

		assertThatThrownBy(() -> simulator.play("high-sweep", 1)).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("already playing");
		simulator.stop();

		await().until(() -> simulator.status().state().equals("idle"));
		assertThat(wire.count.get()).isLessThan(4 * 5_400);
	}

	@Test
	void stopWhileIdleDoesNothing() {
		simulator.stop();

		assertThat(simulator.status().state()).isEqualTo("idle");
	}

	@Test
	void rejectsUnknownScenarioAndBadSpeed() {
		wire.subscribeAll();
		await().until(() -> simulator.status().missing().isEmpty());

		assertThatThrownBy(() -> simulator.play("nope", 60)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("nope");
		assertThatThrownBy(() -> simulator.play("low-sweep", 0)).isInstanceOf(IllegalArgumentException.class);
	}
}

package com.alert_service.demo;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import com.alert_service.marketdata.OhlcvBar;

/**
 * A stand-in market data adapter for trying the UI: plays {@link Scenarios scenarios} of ES, NQ, YM and RTY 1-second
 * bars on a ZeroMQ PUB socket, faster than real time. A scenario covers only the span in which the Sequence range
 * sweep can trigger, from the start of 3-3-2 (09:22:30 New York) to the end of 3-4-1 (10:52:30) of 2026-10-12; see
 * {@link ScenarioPlayer}.
 * <p>
 * It serves a small page ({@link SimulatorServer}, default {@code http://127.0.0.1:5557}) to pick a scenario and a
 * speed, play it and stop it. A scenario can only start once the service is subscribed to all four symbols (open the
 * UI, rule running), since a late subscriber would miss bars. Bars carry the simulated times, so the speed changes
 * nothing in the results. The service keeps the day's bars and rule state: press Reset data in the UI before playing
 * the next scenario.
 * <p>
 * Run with {@code ./gradlew demoAdapter}. Options ({@code --args="..."}): {@code --endpoint} (default
 * {@code tcp://127.0.0.1:5556}; {@code tcp://*:5556} to accept other hosts), {@code --control-host} (default
 * {@code 127.0.0.1}), {@code --control-port} (default 5557), {@code --speed} (default speed when {@code --scenario}
 * is given, simulated seconds per real second, default 60), {@code --scenario} (play that scenario as soon as the
 * service is subscribed, without the page).
 */
public final class DemoAdapter {

	private static final String PREFIX = "OHLCV.";

	private DemoAdapter() {
	}

	public static void main(String[] args) throws InterruptedException {
		String endpoint = option(args, "--endpoint", "tcp://127.0.0.1:5556");
		String controlHost = option(args, "--control-host", "127.0.0.1");
		int controlPort = Integer.parseInt(option(args, "--control-port", "5557"));
		int speed = Integer.parseInt(option(args, "--speed", "60"));
		String scenario = option(args, "--scenario", null);
		try (ZContext context = new ZContext()) {
			ZMQ.Socket socket = context.createSocket(SocketType.XPUB);
			socket.bind(endpoint);
			System.out.println("Demo adapter bound on " + endpoint);
			try (Simulator simulator = new Simulator(new ZmqWire(socket));
					SimulatorServer server = new SimulatorServer(simulator, controlHost, controlPort)) {
				System.out.println("Simulator page on http://" + controlHost + ":" + server.port());
				if (scenario != null) {
					playWhenSubscribed(simulator, scenario, speed);
				}
				Thread.currentThread().join();
			}
		}
	}

	private static void playWhenSubscribed(Simulator simulator, String scenario, int speed)
			throws InterruptedException {
		System.out.println("Waiting for the service to subscribe to " + ScenarioPlayer.SYMBOLS
				+ ": open the UI (the sequence range sweep on ES, NQ, YM, RTY runs by default)");
		while (!simulator.status().missing().isEmpty()) {
			Thread.sleep(200);
		}
		simulator.play(scenario, speed);
		System.out.println("Playing " + scenario + " at " + speed + "x");
	}

	/** The XPUB socket: subscriptions arrive as frames, bars go out as topic + JSON ([[06_market_data_contract]]). */
	private static final class ZmqWire implements Simulator.Wire {

		private final ZMQ.Socket socket;
		private final Set<String> topics = new HashSet<>();

		ZmqWire(ZMQ.Socket socket) {
			this.socket = socket;
		}

		@Override
		public void drainSubscriptions(Set<String> now) {
			byte[] frame;
			while ((frame = socket.recv(ZMQ.DONTWAIT)) != null) {
				if (frame.length > 1) {
					String topic = new String(frame, 1, frame.length - 1, StandardCharsets.UTF_8);
					if (frame[0] == 1) {
						topics.add(topic);
					}
					else if (frame[0] == 0) {
						topics.remove(topic);
					}
				}
			}
			now.addAll(topics);
		}

		@Override
		public void send(OhlcvBar bar) {
			String json = "{\"symbol\":\"%s\",\"startTime\":%d,\"open\":%s,\"high\":%s,\"low\":%s,\"close\":%s,\"volume\":%d}"
				.formatted(bar.symbol(), bar.startTime(), bar.open().toPlainString(), bar.high().toPlainString(),
						bar.low().toPlainString(), bar.close().toPlainString(), bar.volume());
			socket.sendMore(PREFIX + bar.symbol());
			socket.send(json);
		}
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

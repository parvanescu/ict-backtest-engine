package com.alert_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.lang.reflect.Type;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import com.alert_service.bars.BarFeedService;
import com.alert_service.run.RunService;

import tools.jackson.databind.json.JsonMapper;

/**
 * The whole service: a STOMP client subscribes to a rule's signals and to live bars, a ZeroMQ publisher plays the
 * market data adapter, and the worked example comes back live and through the history endpoints.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class StompEndToEndTest {

	private static final String SWEEP = "SEQUENCE_RANGE_SWEEP[ES,NQ,RTY,YM]";

	private static final ZContext ZMQ_CONTEXT = new ZContext();
	private static final ZMQ.Socket ADAPTER = ZMQ_CONTEXT.createSocket(SocketType.XPUB);
	private static final int ADAPTER_PORT = ADAPTER.bindToRandomPort("tcp://127.0.0.1");

	@DynamicPropertySource
	static void adapterEndpoint(DynamicPropertyRegistry registry) {
		registry.add("alert.zmq.endpoints", () -> "tcp://127.0.0.1:" + ADAPTER_PORT);
	}

	@AfterAll
	static void closeAdapter() {
		ZMQ_CONTEXT.close();
	}

	@LocalServerPort
	int port;

	@Autowired
	RunService runService;

	@Autowired
	BarFeedService barFeeds;

	private final JsonMapper json = JsonMapper.builder().build();
	private final HttpClient http = HttpClient.newHttpClient();
	private StompSession session;

	@BeforeEach
	void connect() throws Exception {
		WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
		client.setMessageConverter(new JacksonJsonMessageConverter());
		session = client.connectAsync("ws://127.0.0.1:" + port + "/ws", new StompSessionHandlerAdapter() {
		}).get(5, TimeUnit.SECONDS);
	}

	@AfterEach
	void disconnect() {
		if (session.isConnected()) {
			session.disconnect();
		}
	}

	@Test
	void workedExampleLiveAndThroughHistory() throws Exception {
		BlockingQueue<Map<String, Object>> signals = subscribe("/topic/signals/" + SWEEP);
		BlockingQueue<Map<String, Object>> esBars = subscribe("/topic/bars/ES");
		await().atMost(Duration.ofSeconds(5))
			.until(() -> runService.runs().containsKey(SWEEP) && barFeeds.watchers().containsKey("ES"));
		awaitAdapterSubscriptions(Set.of("OHLCV.ES", "OHLCV.NQ", "OHLCV.YM", "OHLCV.RTY"));

		String[][] ranges = { { "ES", "6032.00", "6026.00" }, { "NQ", "21120.00", "21090.00" },
				{ "YM", "44240.00", "44180.00" }, { "RTY", "2185.00", "2178.00" } };
		for (String[] r : ranges) {
			publish(r[0], "2026-10-12T13:00:00Z", r[1], r[2]);
			publish(r[0], "2026-10-12T13:45:00Z", r[1], r[2]);
		}
		publish("ES", "2026-10-12T14:12:40Z", "6028.00", "6027.00");
		publish("NQ", "2026-10-12T14:12:40Z", "21110.00", "21108.00");
		publish("RTY", "2026-10-12T14:12:40Z", "2179.50", "2179.20");
		publish("YM", "2026-10-12T14:12:40Z", "44175.00", "44168.00");

		List<Map<String, Object>> live = take(signals, 4);
		assertThat(live).extracting(s -> s.get("id")).containsExactly(1, 2, 3, 4);
		assertThat(live.getFirst()).containsEntry("kind", "REPORT")
			.containsEntry("code", "EXTREMITY_PASSED")
			.containsEntry("scope", List.of("YM"));
		List<Map<String, Object>> checks = live.subList(1, 4);
		assertThat(checks).allSatisfy(s -> assertThat(s).containsEntry("kind", "REPORT")
			.doesNotContainKey("verdict")
			.containsEntry("eventTime", "2026-10-12T14:12:40Z"));
		assertThat(checks).filteredOn(s -> "RANGE_SWEEP_EVENT".equals(s.get("code")))
			.extracting(s -> String.valueOf(((List<?>) s.get("scope")).getFirst()))
			.containsExactlyInAnyOrder("ES", "RTY");
		assertThat(checks).filteredOn(s -> "RANGE_SWEEP_NO_EVENT".equals(s.get("code")))
			.extracting(s -> String.valueOf(((List<?>) s.get("scope")).getFirst()))
			.containsExactly("NQ");

		List<Map<String, Object>> bars = take(esBars, 3);
		assertThat(bars).allSatisfy(b -> assertThat(b).containsEntry("type", "BAR").containsEntry("symbol", "ES"));
		assertThat(bars).extracting(b -> b.get("time"))
			.containsExactly("2026-10-12T13:00:00Z", "2026-10-12T13:45:00Z", "2026-10-12T14:12:40Z");

		Map<String, Object> barHistory = get("/api/bars/ES?from=2026-10-12T13:45:00Z", 200);
		assertThat(barHistory).containsEntry("tradingDay", "2026-10-12");
		assertThat((List<?>) barHistory.get("bars")).hasSize(2);

		Map<String, Object> signalHistory = get("/api/signals/" + URLEncoder.encode(SWEEP, StandardCharsets.UTF_8),
				200);
		assertThat(signalHistory).containsEntry("dayStatus", "VALID").containsEntry("tradingDay", "2026-10-12");
		assertThat((List<?>) signalHistory.get("signals")).isEqualTo(live);
	}

	@Test
	void invalidSubscriptionsGetAnErrorOnTheSameTopic() throws Exception {
		BlockingQueue<Map<String, Object>> badBars = subscribe("/topic/bars/es");
		BlockingQueue<Map<String, Object>> unsorted = subscribe("/topic/signals/SEQUENCE_RANGE_SWEEP[YM,ES]");
		BlockingQueue<Map<String, Object>> unknown = subscribe("/topic/signals/NOPE[ES]");

		assertThat(take(badBars, 1).getFirst()).containsEntry("type", "ERROR")
			.hasEntrySatisfying("message", m -> assertThat((String) m).contains("Invalid symbol"));
		assertThat(take(unsorted, 1).getFirst()).containsEntry("kind", "ERROR")
			.containsEntry("code", "INVALID_SUBSCRIPTION")
			.hasEntrySatisfying("message", m -> assertThat((String) m).contains("SEQUENCE_RANGE_SWEEP[ES,YM]"));
		assertThat(take(unknown, 1).getFirst()).containsEntry("kind", "ERROR")
			.hasEntrySatisfying("message", m -> assertThat((String) m).contains("Unknown rule"));
		assertThat(runService.runs()).isEmpty();
	}

	@Test
	void unsubscribeAndDisconnectReleaseRulesAndFeeds() throws Exception {
		StompSession.Subscription rule = session.subscribe("/topic/signals/NEVER_FIRING[ZZ]", collectInto(
				new LinkedBlockingQueue<>()));
		subscribe("/topic/bars/ZZ");
		await().atMost(Duration.ofSeconds(5))
			.until(() -> runService.runs().containsKey("NEVER_FIRING[ZZ]") && barFeeds.watchers().containsKey("ZZ"));

		rule.unsubscribe();
		await().atMost(Duration.ofSeconds(5)).until(() -> !runService.runs().containsKey("NEVER_FIRING[ZZ]"));
		assertThat(barFeeds.watchers()).containsKey("ZZ");

		session.disconnect();
		await().atMost(Duration.ofSeconds(5)).until(() -> !barFeeds.watchers().containsKey("ZZ"));
	}

	@Test
	void historyEndpointsRejectBadRequests() throws Exception {
		assertThat(get("/api/bars/e$", 400)).containsKey("error");
		assertThat(get("/api/bars/ES?from=yesterday", 400)).containsKey("error");
		assertThat(get("/api/signals/" + URLEncoder.encode("NEVER_FIRING[QQ]", StandardCharsets.UTF_8), 404))
			.hasEntrySatisfying("error", e -> assertThat((String) e).contains("not running"));
	}

	private BlockingQueue<Map<String, Object>> subscribe(String destination) {
		BlockingQueue<Map<String, Object>> queue = new LinkedBlockingQueue<>();
		session.subscribe(destination, collectInto(queue));
		return queue;
	}

	private static List<Map<String, Object>> take(BlockingQueue<Map<String, Object>> queue, int count)
			throws InterruptedException {
		List<Map<String, Object>> taken = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			Map<String, Object> message = queue.poll(10, TimeUnit.SECONDS);
			assertThat(message).as("message %d", i + 1).isNotNull();
			taken.add(message);
		}
		return taken;
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> get(String path, int expectedStatus) throws Exception {
		HttpResponse<String> response = http.send(
				HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET().build(),
				HttpResponse.BodyHandlers.ofString());
		assertThat(response.statusCode()).as("GET %s: %s", path, response.body()).isEqualTo(expectedStatus);
		return json.readValue(response.body(), Map.class);
	}

	/** XPUB receives one frame per new subscription: byte 1 followed by the topic. */
	private static void awaitAdapterSubscriptions(Set<String> topics) {
		Set<String> seen = new HashSet<>();
		ADAPTER.setReceiveTimeOut(200);
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
		while (!seen.containsAll(topics) && System.nanoTime() < deadline) {
			byte[] frame = ADAPTER.recv();
			if (frame != null && frame.length > 1 && frame[0] == 1) {
				seen.add(new String(frame, 1, frame.length - 1, StandardCharsets.UTF_8));
			}
		}
		assertThat(seen).containsAll(topics);
	}

	private static void publish(String symbol, String instant, String high, String low) {
		long nanos = Instant.parse(instant).getEpochSecond() * 1_000_000_000L;
		ADAPTER.sendMore("OHLCV." + symbol);
		ADAPTER.send("""
				{"symbol":"%s","startTime":%d,"open":%s,"high":%s,"low":%s,"close":%s,"volume":1}"""
			.formatted(symbol, nanos, low, high, low, high));
	}

	private static StompFrameHandler collectInto(BlockingQueue<Map<String, Object>> queue) {
		return new StompFrameHandler() {
			@Override
			public Type getPayloadType(StompHeaders headers) {
				return Map.class;
			}

			@Override
			@SuppressWarnings("unchecked")
			public void handleFrame(StompHeaders headers, Object payload) {
				queue.add((Map<String, Object>) payload);
			}
		};
	}
}

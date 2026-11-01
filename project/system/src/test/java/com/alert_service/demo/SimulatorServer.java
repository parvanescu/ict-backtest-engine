package com.alert_service.demo;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The simulator's own web page and its small JSON API: list the scenarios, play one at a chosen speed, stop it, read
 * the status. No authentication: bind it to the loopback (the default) or publish its port on the loopback only.
 */
public final class SimulatorServer implements AutoCloseable {

	private static final int DEFAULT_SPEED = 60;

	private final Simulator simulator;
	private final JsonMapper json = JsonMapper.builder().build();
	private final HttpServer server;

	/**
	 * @param port 0 picks a free port, see {@link #port()}
	 */
	public SimulatorServer(Simulator simulator, String host, int port) {
		this.simulator = simulator;
		try {
			server = HttpServer.create(new InetSocketAddress(host, port), 0);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
		server.createContext("/", this::handle);
		server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
		server.start();
	}

	public int port() {
		return server.getAddress().getPort();
	}

	@Override
	public void close() {
		server.stop(0);
	}

	private void handle(HttpExchange exchange) throws IOException {
		try {
			String route = exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath();
			switch (route) {
				case "GET /" -> page(exchange);
				case "GET /api/scenarios" -> reply(exchange, 200, scenarios());
				case "GET /api/status" -> reply(exchange, 200, simulator.status());
				case "POST /api/play" -> play(exchange);
				case "POST /api/stop" -> {
					simulator.stop();
					reply(exchange, 200, Map.of("ok", true));
				}
				default -> reply(exchange, 404, Map.of("error", "Not found: " + route));
			}
		}
		catch (IllegalArgumentException ex) {
			reply(exchange, 400, Map.of("error", String.valueOf(ex.getMessage())));
		}
		catch (IllegalStateException ex) {
			reply(exchange, 409, Map.of("error", String.valueOf(ex.getMessage())));
		}
		finally {
			exchange.close();
		}
	}

	private void play(HttpExchange exchange) throws IOException {
		JsonNode body;
		try {
			body = json.readTree(exchange.getRequestBody());
		}
		catch (RuntimeException ex) {
			throw new IllegalArgumentException("The body must be JSON like {\"scenario\":\"low-sweep\",\"speed\":60}");
		}
		String scenario = body.path("scenario").asString("");
		int speed = body.path("speed").asInt(DEFAULT_SPEED);
		simulator.play(scenario, speed);
		reply(exchange, 200, simulator.status());
	}

	private List<Map<String, Object>> scenarios() {
		return Scenarios.ALL.stream()
			.<Map<String, Object>>map(s -> Map.of("id", s.id(), "title", s.title(), "description", s.description(),
					"expected", s.expected()))
			.toList();
	}

	private void page(HttpExchange exchange) throws IOException {
		try (InputStream page = SimulatorServer.class.getResourceAsStream("/simulator/index.html")) {
			if (page == null) {
				reply(exchange, 500, Map.of("error", "Simulator page missing from the classpath"));
				return;
			}
			send(exchange, 200, "text/html; charset=utf-8", page.readAllBytes());
		}
	}

	private void reply(HttpExchange exchange, int status, Object body) throws IOException {
		send(exchange, status, "application/json", json.writeValueAsBytes(body));
	}

	private void send(HttpExchange exchange, int status, String type, byte[] body) throws IOException {
		exchange.getResponseHeaders().set("Content-Type", type);
		exchange.getResponseHeaders().set("Cache-Control", "no-store");
		exchange.sendResponseHeaders(status, body.length);
		exchange.getResponseBody().write(body);
	}
}

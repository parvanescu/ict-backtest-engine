package com.alert_service.demo;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class SimulatorServerTest {

	private final SimulatorTest.FakeWire wire = new SimulatorTest.FakeWire();
	private final Simulator simulator = new Simulator(wire);
	private final SimulatorServer server = new SimulatorServer(simulator, "127.0.0.1", 0);
	private final HttpClient http = HttpClient.newHttpClient();
	private final JsonMapper json = JsonMapper.builder().build();

	@AfterEach
	void close() {
		server.close();
		simulator.close();
	}

	private HttpResponse<String> get(String path) throws Exception {
		return http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + path)).build(),
				BodyHandlers.ofString());
	}

	private HttpResponse<String> post(String path, String body) throws Exception {
		return http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + path))
			.header("Content-Type", "application/json")
			.POST(BodyPublishers.ofString(body))
			.build(), BodyHandlers.ofString());
	}

	@Test
	void servesThePage() throws Exception {
		HttpResponse<String> page = get("/");

		assertThat(page.statusCode()).isEqualTo(200);
		assertThat(page.headers().firstValue("Content-Type")).hasValueSatisfying(type -> assertThat(type).startsWith("text/html"));
		assertThat(page.body()).contains("Play").contains("Stop");
	}

	@Test
	void listsTheFourScenariosWithTheirExpectedEvents() throws Exception {
		JsonNode scenarios = json.readTree(get("/api/scenarios").body());

		assertThat(scenarios).hasSize(4);
		assertThat(scenarios.get(0).get("id").asString()).isEqualTo("low-sweep");
		assertThat(scenarios.get(0).get("expected")).isNotEmpty();
	}

	@Test
	void unknownScenarioIsABadRequest() throws Exception {
		wire.subscribeAll();
		org.awaitility.Awaitility.await().until(() -> simulator.status().missing().isEmpty());

		HttpResponse<String> response = post("/api/play", "{\"scenario\":\"nope\",\"speed\":60}");

		assertThat(response.statusCode()).isEqualTo(400);
		assertThat(json.readTree(response.body()).get("error").asString()).contains("nope");
	}

	@Test
	void playingBeforeTheServiceSubscribedIsAConflictNamingTheMissingSymbols() throws Exception {
		HttpResponse<String> response = post("/api/play", "{\"scenario\":\"low-sweep\",\"speed\":60}");

		assertThat(response.statusCode()).isEqualTo(409);
		assertThat(json.readTree(response.body()).get("error").asString()).contains("ES", "NQ", "YM", "RTY");
	}

	@Test
	void statusReportsStateAndMissingSymbols() throws Exception {
		JsonNode status = json.readTree(get("/api/status").body());

		assertThat(status.get("state").asString()).isEqualTo("idle");
		assertThat(status.get("missing")).hasSize(4);
	}
}

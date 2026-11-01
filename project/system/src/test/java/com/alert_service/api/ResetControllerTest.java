package com.alert_service.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class ResetControllerTest {

	@LocalServerPort
	int port;

	private final HttpClient http = HttpClient.newHttpClient();

	private HttpRequest.Builder reset() {
		return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/reset"));
	}

	@Test
	void resetWithJsonBodySucceedsWhenNothingIsStored() throws Exception {
		HttpResponse<String> response = http.send(
				reset().header("Content-Type", "application/json").POST(BodyPublishers.ofString("{}")).build(),
				BodyHandlers.ofString());

		assertThat(response.statusCode()).isEqualTo(204);
	}

	@Test
	void resetWithoutJsonContentTypeIsRefused() throws Exception {
		HttpResponse<String> response = http.send(
				reset().header("Content-Type", "text/plain").POST(BodyPublishers.ofString("x")).build(),
				BodyHandlers.ofString());

		assertThat(response.statusCode()).isEqualTo(415);
	}

	@Test
	void foreignOriginGetsNoCorsApproval() throws Exception {
		HttpResponse<String> response = http.send(reset().header("Origin", "http://evil.example")
			.header("Access-Control-Request-Method", "POST")
			.method("OPTIONS", BodyPublishers.noBody())
			.build(), BodyHandlers.ofString());

		assertThat(response.headers().firstValue("Access-Control-Allow-Origin")).isEmpty();
	}
}

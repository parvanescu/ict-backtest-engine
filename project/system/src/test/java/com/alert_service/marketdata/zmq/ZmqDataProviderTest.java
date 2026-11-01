package com.alert_service.marketdata.zmq;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import com.alert_service.marketdata.DataType;
import com.alert_service.marketdata.OhlcvBar;
import com.alert_service.marketdata.OhlcvJsonDecoder;
import com.alert_service.marketdata.Topic;

import tools.jackson.databind.json.JsonMapper;

class ZmqDataProviderTest {

	private static final Topic ES = new Topic(DataType.OHLCV, "ES");
	private static final String ES_BAR = """
			{"symbol":"ES","startTime":1000000000,"open":6030.00,"high":6031.00,"low":6029.50,"close":6030.25,"volume":7}""";

	private final OhlcvJsonDecoder decoder = new OhlcvJsonDecoder(JsonMapper.builder().build());

	private static byte[] bytes(String s) {
		return s.getBytes(StandardCharsets.UTF_8);
	}

	@Test
	void handleDispatchesValidMessagesOnly() {
		List<OhlcvBar> received = new ArrayList<>();
		ZmqDataProvider<OhlcvBar> provider = new ZmqDataProvider<>(ES, decoder, () -> {
		}, null, List.of(), 100);
		provider.subscribe("test", received::add);

		provider.handle("OHLCV.ES", bytes(ES_BAR));
		provider.handle("OHLCV.ES2", bytes(ES_BAR.replace("\"ES\"", "\"ES2\"")));
		provider.handle("OHLCV.ES", bytes(ES_BAR.replace("\"ES\"", "\"NQ\"")));
		provider.handle("OHLCV.ES", bytes("garbage"));

		assertThat(received).hasSize(1);
		assertThat(received.getFirst().symbol()).isEqualTo("ES");
	}

	@Test
	void receivesBarsFromRealPublisher() throws Exception {
		try (ZContext testContext = new ZContext()) {
			ZMQ.Socket publisher = testContext.createSocket(SocketType.XPUB);
			int port = publisher.bindToRandomPort("tcp://127.0.0.1");
			ZmqDataProviderFactory factory = new ZmqDataProviderFactory(
					new ZmqProperties(List.of("tcp://127.0.0.1:" + port), Duration.ofMillis(50)));
			BlockingQueue<OhlcvBar> received = new LinkedBlockingQueue<>();
			try {
				DataProvider<OhlcvBar> provider = factory.create(ES, decoder, () -> {
				});
				provider.subscribe("test", received::add);

				publisher.setReceiveTimeOut(5_000);
				byte[] subscription = publisher.recv();
				assertThat(subscription).isNotNull();
				assertThat(new String(subscription, 1, subscription.length - 1, StandardCharsets.UTF_8))
					.isEqualTo("OHLCV.ES");

				publisher.sendMore("OHLCV.ES2");
				publisher.send(ES_BAR.replace("\"ES\"", "\"ES2\""));
				publisher.sendMore("OHLCV.ES");
				publisher.send(ES_BAR);

				OhlcvBar bar = received.poll(5, TimeUnit.SECONDS);
				assertThat(bar).isNotNull();
				assertThat(bar.symbol()).isEqualTo("ES");
				assertThat(received.poll(200, TimeUnit.MILLISECONDS)).isNull();
			}
			finally {
				factory.close();
			}
		}
	}
}

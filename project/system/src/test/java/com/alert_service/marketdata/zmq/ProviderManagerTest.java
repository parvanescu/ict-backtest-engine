package com.alert_service.marketdata.zmq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.alert_service.event.Subscription;
import com.alert_service.marketdata.DataType;
import com.alert_service.marketdata.MarketDataDecoder;
import com.alert_service.marketdata.OhlcvBar;
import com.alert_service.marketdata.Topic;
import com.alert_service.testing.FakeDataProviderFactory;

class ProviderManagerTest {

	private static final Topic ES = new Topic(DataType.OHLCV, "ES");
	private static final Topic NQ = new Topic(DataType.OHLCV, "NQ");

	private final MarketDataDecoder<OhlcvBar> decoder = new MarketDataDecoder<>() {
		@Override
		public DataType dataType() {
			return DataType.OHLCV;
		}

		@Override
		public OhlcvBar decode(Topic topic, byte[] payload) {
			throw new UnsupportedOperationException();
		}
	};
	private final FakeDataProviderFactory factory = new FakeDataProviderFactory();
	private final ProviderManager manager = new ProviderManager(factory, List.of(decoder));

	private Subscription subscribe(Topic topic, String subscriberId) {
		return manager.subscribe(topic, OhlcvBar.class, subscriberId, bar -> {
		});
	}

	@Test
	void sharesOneProviderPerTopic() {
		subscribe(ES, "a");
		subscribe(ES, "b");
		subscribe(NQ, "a");

		assertThat(factory.created()).hasSize(2);
		assertThat(manager.topics()).containsExactlyInAnyOrder(ES, NQ);
	}

	@Test
	void closesProviderWhenLastListenerLeaves() {
		Subscription a = subscribe(ES, "a");
		Subscription b = subscribe(ES, "b");

		a.close();
		assertThat(factory.isOpen(ES)).isTrue();
		b.close();

		assertThat(factory.created().getFirst().isClosed()).isTrue();
		assertThat(manager.topics()).isEmpty();
	}

	@Test
	void createsFreshProviderAfterClose() {
		subscribe(ES, "a").close();
		subscribe(ES, "a");

		assertThat(factory.created()).hasSize(2);
		assertThat(factory.created().get(1).isClosed()).isFalse();
		assertThat(manager.topics()).containsExactly(ES);
	}

	@Test
	void rejectsWrongEventType() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> manager.subscribe(ES, String.class, "a", event -> {
			}));
	}
}

package com.alert_service.bars;

import static com.alert_service.testing.Bars.bar;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.alert_service.event.Subscription;
import com.alert_service.marketdata.DataType;
import com.alert_service.marketdata.OhlcvBar;
import com.alert_service.marketdata.OhlcvJsonDecoder;
import com.alert_service.marketdata.Topic;
import com.alert_service.marketdata.zmq.ProviderManager;
import com.alert_service.run.SessionSubscriptions.Ref;
import com.alert_service.testing.FakeDataProviderFactory;
import com.alert_service.timegrid.TimeGrid;

import tools.jackson.databind.json.JsonMapper;

class BarFeedServiceTest {

	private static final Topic ES = new Topic(DataType.OHLCV, "ES");

	private record Published(String symbol, BarMessage message) {
	}

	private final FakeDataProviderFactory providers = new FakeDataProviderFactory();
	private final ProviderManager manager = new ProviderManager(providers,
			List.of(new OhlcvJsonDecoder(JsonMapper.builder().build())));
	private final BarStore store = new BarStore(TimeGrid.cme());
	private final List<Published> published = new ArrayList<>();
	private final BarFeedService feeds = new BarFeedService(manager, store,
			(symbol, message) -> published.add(new Published(symbol, message)));

	@Test
	void watchingOpensOneProviderSubscriptionPerSymbol() {
		feeds.watch(new Ref("A", "sub-0"), "ES");
		feeds.watch(new Ref("B", "sub-0"), "ES");

		assertThat(providers.created()).hasSize(1);
		assertThat(feeds.watchers()).isEqualTo(Map.of("ES", Set.of("A", "B")));
	}

	@Test
	void barsArePublishedAndStored() {
		feeds.watch(new Ref("A", "sub-0"), "ES");

		providers.<OhlcvBar>open(ES).push(bar("ES", "2026-10-12T13:45:00Z", "6032.00", "6026.00"));

		assertThat(published).singleElement().satisfies(p -> {
			assertThat(p.symbol()).isEqualTo("ES");
			assertThat(p.message().type()).isEqualTo(BarMessage.BAR);
			assertThat(p.message().time()).isEqualTo("2026-10-12T13:45:00Z");
		});
		assertThat(store.since("ES", null).bars()).hasSize(1);
	}

	@Test
	void sharesTheProviderARuleAlreadyOpened() {
		Subscription rule = manager.subscribe(ES, OhlcvBar.class, "RANGE_BREAK_WATCH[ES]", bar -> {
		});
		feeds.watch(new Ref("A", "sub-0"), "ES");

		feeds.unwatch(new Ref("A", "sub-0"));

		assertThat(providers.created()).hasSize(1);
		assertThat(providers.isOpen(ES)).isTrue();
		rule.close();
		assertThat(providers.isOpen(ES)).isFalse();
	}

	@Test
	void lastWatcherLeavingClosesTheProvider() {
		feeds.watch(new Ref("A", "sub-0"), "ES");
		feeds.watch(new Ref("A", "sub-1"), "NQ");

		feeds.unwatchAll("A");

		assertThat(feeds.watchers()).isEmpty();
		assertThat(providers.created()).allSatisfy(p -> assertThat(p.isClosed()).isTrue());
	}

	@Test
	void rejectsInvalidSymbols() {
		assertThatIllegalArgumentException().isThrownBy(() -> feeds.watch(new Ref("A", "sub-0"), "es"));
		assertThatIllegalArgumentException().isThrownBy(() -> feeds.watch(new Ref("A", "sub-0"), "E$"));

		assertThat(providers.created()).isEmpty();
	}
}

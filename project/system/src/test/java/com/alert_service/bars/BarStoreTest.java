package com.alert_service.bars;

import static com.alert_service.testing.Bars.bar;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.alert_service.bars.BarStore.Snapshot;
import com.alert_service.marketdata.OhlcvBar;
import com.alert_service.timegrid.TimeGrid;

class BarStoreTest {

	private final BarStore store = new BarStore(TimeGrid.cme());

	@Test
	void keepsBarsInOrderWithExactPrices() {
		store.append(new OhlcvBar("ES", nanos("2026-10-12T13:45:00Z"), new BigDecimal("6030.25"),
				new BigDecimal("6032.00"), new BigDecimal("6026.75"), new BigDecimal("6031.50"), 42));
		store.append(bar("ES", "2026-10-12T13:45:01Z", "6031.75", "6031.25"));

		Snapshot snapshot = store.since("ES", null);

		assertThat(snapshot.tradingDay()).isEqualTo(LocalDate.of(2026, 10, 12));
		assertThat(snapshot.bars()).hasSize(2);
		OhlcvBar first = snapshot.bars().getFirst();
		assertThat(first.startInstant()).isEqualTo(Instant.parse("2026-10-12T13:45:00Z"));
		assertThat(first.open()).isEqualTo(new BigDecimal("6030.25"));
		assertThat(first.high()).isEqualTo(new BigDecimal("6032"));
		assertThat(first.low()).isEqualTo(new BigDecimal("6026.75"));
		assertThat(first.close()).isEqualTo(new BigDecimal("6031.5"));
		assertThat(first.volume()).isEqualTo(42);
	}

	@Test
	void sinceIsInclusiveAndBinarySearched() {
		for (int s = 0; s < 10; s++) {
			store.append(bar("NQ", Instant.parse("2026-10-12T13:45:00Z").plusSeconds(s).toString(), "21100"));
		}

		assertThat(store.since("NQ", Instant.parse("2026-10-12T13:45:07Z")).bars()).hasSize(3);
		assertThat(store.since("NQ", Instant.parse("2026-10-12T13:45:07.500Z")).bars()).hasSize(2);
		assertThat(store.since("NQ", Instant.parse("2026-10-12T13:46:00Z")).bars()).isEmpty();
	}

	@Test
	void unknownSymbolHasNothing() {
		Snapshot snapshot = store.since("YM", null);

		assertThat(snapshot.tradingDay()).isNull();
		assertThat(snapshot.bars()).isEmpty();
	}

	@Test
	void newTradingDayDropsThePreviousOne() {
		store.append(bar("ES", "2026-10-12T21:59:59Z", "6030"));
		store.append(bar("ES", "2026-10-12T22:00:00Z", "6031"));

		Snapshot snapshot = store.since("ES", null);

		assertThat(snapshot.tradingDay()).isEqualTo(LocalDate.of(2026, 10, 13));
		assertThat(snapshot.bars()).singleElement()
			.satisfies(b -> assertThat(b.close()).isEqualByComparingTo("6031"));
	}

	@Test
	void ignoresBarsThatAreNotNewer() {
		store.append(bar("ES", "2026-10-12T13:45:01Z", "6031"));
		store.append(bar("ES", "2026-10-12T13:45:01Z", "6099"));
		store.append(bar("ES", "2026-10-12T13:45:00Z", "6099"));

		assertThat(store.since("ES", null).bars()).singleElement()
			.satisfies(b -> assertThat(b.close()).isEqualByComparingTo("6031"));
	}

	@Test
	void doesNotStorePricesWithTooManyDecimals() {
		store.append(bar("ES", "2026-10-12T13:45:00Z", "6031.1234567"));
		store.append(bar("ES", "2026-10-12T13:45:01Z", "6031.123456"));

		assertThat(store.since("ES", null).bars()).singleElement()
			.satisfies(b -> assertThat(b.close()).isEqualTo(new BigDecimal("6031.123456")));
	}

	@Test
	void growsToAFullDay() {
		Instant start = Instant.parse("2026-10-11T22:00:00Z");
		for (int s = 0; s < BarStore.MAX_BARS_PER_DAY; s++) {
			store.append(new OhlcvBar("RTY", nanos(start.plusSeconds(s)), BigDecimal.ONE, BigDecimal.ONE,
					BigDecimal.ONE, BigDecimal.ONE, 1));
		}

		Snapshot snapshot = store.since("RTY", null);
		assertThat(snapshot.bars()).hasSize(BarStore.MAX_BARS_PER_DAY);
		assertThat(snapshot.bars().getLast().startInstant()).isEqualTo(Instant.parse("2026-10-12T21:59:59Z"));
	}

	private static long nanos(String instant) {
		return nanos(Instant.parse(instant));
	}

	private static long nanos(Instant instant) {
		return instant.getEpochSecond() * 1_000_000_000L;
	}
}

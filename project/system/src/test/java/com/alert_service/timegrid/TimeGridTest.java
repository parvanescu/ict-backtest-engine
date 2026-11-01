package com.alert_service.timegrid;

import static com.alert_service.timegrid.GridLevel.L1_6H;
import static com.alert_service.timegrid.GridLevel.L2_90M;
import static com.alert_service.timegrid.GridLevel.L3_22_5M;
import static com.alert_service.timegrid.GridLevel.L4_5_625M;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class TimeGridTest {

	private final TimeGrid grid = TimeGrid.cme();

	private GridPosition at(String instant) {
		return grid.position(Instant.parse(instant));
	}

	private static int[] address(GridPosition p) {
		return new int[] { p.quarter(L1_6H), p.quarter(L2_90M), p.quarter(L3_22_5M), p.quarter(L4_5_625M) };
	}

	@Test
	void block333OnFirstEdtTradingDay() {
		GridPosition p = at("2026-03-09T13:45:00Z");

		assertThat(p.tradingDay()).isEqualTo(LocalDate.of(2026, 3, 9));
		assertThat(p.dayStart()).isEqualTo(Instant.parse("2026-03-08T22:00:00Z"));
		assertThat(address(p)).containsExactly(2, 2, 2, 0);
		assertThat(p.block(L3_22_5M)).isEqualTo(42);
	}

	@Test
	void block333DuringEst() {
		GridPosition p = at("2026-01-16T14:45:00Z");

		assertThat(p.tradingDay()).isEqualTo(LocalDate.of(2026, 1, 16));
		assertThat(p.dayStart()).isEqualTo(Instant.parse("2026-01-15T23:00:00Z"));
		assertThat(address(p)).containsExactly(2, 2, 2, 0);
		assertThat(p.block(L3_22_5M)).isEqualTo(42);
	}

	@Test
	void lastSecondBefore333() {
		assertThat(address(at("2026-01-16T14:44:59Z"))).containsExactly(2, 2, 1, 3);
	}

	@Test
	void sundayOpenIsMondaysTradingDay() {
		GridPosition p = at("2026-11-01T23:00:00Z");

		assertThat(p.tradingDay()).isEqualTo(LocalDate.of(2026, 11, 2));
		assertThat(p.offsetNanos()).isZero();
		assertThat(address(p)).containsExactly(0, 0, 0, 0);
	}

	@Test
	void windowsAreHalfOpen() {
		GridPosition lastLondonSecond = at("2026-10-12T09:59:59Z");
		GridPosition firstNyAmSecond = at("2026-10-12T10:00:00Z");

		assertThat(lastLondonSecond.quarter(L1_6H)).isEqualTo(1);
		assertThat(firstNyAmSecond.quarter(L1_6H)).isEqualTo(2);
		assertThat(firstNyAmSecond.windowStart(L1_6H)).isEqualTo(Instant.parse("2026-10-12T10:00:00Z"));
		assertThat(firstNyAmSecond.windowEnd(L1_6H)).isEqualTo(Instant.parse("2026-10-12T16:00:00Z"));
	}

	@Test
	void halfSecondBoundaryBelongsToEarlierWindowUntilNextWholeSecond() {
		Instant l3Start = Instant.parse("2026-10-12T13:45:00Z");

		assertThat(grid.position(l3Start.plusSeconds(337)).quarter(L4_5_625M)).isZero();
		assertThat(grid.position(l3Start.plusSeconds(338)).quarter(L4_5_625M)).isEqualTo(1);
		assertThat(grid.position(l3Start.plusSeconds(1012)).quarter(L4_5_625M)).isEqualTo(2);
		assertThat(grid.position(l3Start.plusSeconds(1013)).quarter(L4_5_625M)).isEqualTo(3);
	}

	@Test
	void fourthLevelWindowsCarryTheExactHalfSecond() {
		GridPosition p = at("2026-10-12T13:50:38Z");

		assertThat(p.windowStart(L4_5_625M)).isEqualTo(Instant.parse("2026-10-12T13:50:37.500Z"));
		assertThat(p.windowEnd(L4_5_625M)).isEqualTo(Instant.parse("2026-10-12T13:56:15Z"));
	}

	@Test
	void blockStartOfTheDay() {
		GridPosition p = at("2026-10-12T02:00:00Z");

		assertThat(p.blockStart(L3_22_5M, 42)).isEqualTo(Instant.parse("2026-10-12T13:45:00Z"));
		assertThat(p.blockStart(L3_22_5M, 44)).isEqualTo(Instant.parse("2026-10-12T14:30:00Z"));
	}

	@Test
	void epochNanosMatchInstant() {
		long nanos = Instant.parse("2026-10-12T13:45:00Z").getEpochSecond() * 1_000_000_000L;

		assertThat(grid.positionOfEpochNanos(nanos)).isEqualTo(at("2026-10-12T13:45:00Z"));
	}

	@Test
	void blockCountsPerDay() {
		assertThat(L1_6H.blocksPerDay()).isEqualTo(4);
		assertThat(L2_90M.blocksPerDay()).isEqualTo(16);
		assertThat(L3_22_5M.blocksPerDay()).isEqualTo(64);
		assertThat(L4_5_625M.blocksPerDay()).isEqualTo(256);
	}

	@Test
	void everyWeekdayTradingDayLasts24HoursAcrossYears() {
		for (LocalDate date = LocalDate.of(2020, 1, 1); date.isBefore(LocalDate.of(2036, 1, 1)); date = date.plusDays(1)) {
			if (date.getDayOfWeek() == DayOfWeek.FRIDAY || date.getDayOfWeek() == DayOfWeek.SATURDAY) {
				continue;
			}
			Instant start = date.atTime(TimeGrid.CME_OPEN).atZone(TimeGrid.NEW_YORK).toInstant();
			Instant next = date.plusDays(1).atTime(TimeGrid.CME_OPEN).atZone(TimeGrid.NEW_YORK).toInstant();

			assertThat(Duration.between(start, next)).as("trading day opening %s", date).isEqualTo(Duration.ofHours(24));
			assertThat(grid.position(next.minusNanos(1)).dayStart()).isEqualTo(start);
		}
	}

	@Test
	void extraHourOfFallBackWeekendBelongsToNoTradingDay() {
		// Sunday 2026-11-01 17:30 EST: 24.5 h after Saturday 18:00 EDT
		assertThatIllegalArgumentException().isThrownBy(() -> at("2026-11-01T22:30:00Z"));
	}
}

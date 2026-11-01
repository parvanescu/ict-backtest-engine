package com.alert_service.rule.aggregate.sequencerangesweep;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.alert_service.rule.DayStatus;
import com.alert_service.rule.RuleKey;
import com.alert_service.rule.RuleResult;
import com.alert_service.rule.RuleResult.Kind;
import com.alert_service.rule.Verdict;
import com.alert_service.rule.basic.rangebreak.RangeBreakEvent.BarPositioned;
import com.alert_service.rule.basic.rangebreak.RangeBreakEvent.RangeCaptured;
import com.alert_service.rule.basic.rangebreak.RangeBreakEvent.RangeUnavailable;
import com.alert_service.rule.basic.rangebreak.RangeBreakEvent.RangeUnavailable.Reason;
import com.alert_service.rule.basic.rangebreak.RangeBreakEvent.WindowClosed;

/**
 * Prices from the worked example in the rule note. Trading day 2026-10-12; the trigger second 14:12:40 UTC is
 * 10:12:40 New York.
 */
class SequenceRangeSweepTest {

	private static final LocalDate DAY = LocalDate.of(2026, 10, 12);
	private static final String S = "2026-10-12T14:12:40Z";

	private static final Map<String, String[]> RANGES = Map.of(
			"ES", new String[] { "6032.00", "6026.00", "6029.00" },
			"NQ", new String[] { "21120.00", "21090.00", "21105.00" },
			"YM", new String[] { "44240.00", "44180.00", "44210.00" },
			"RTY", new String[] { "2185.00", "2178.00", "2181.50" });

	private final SequenceRangeSweep rule = new SequenceRangeSweep(
			RuleKey.of("SEQUENCE_RANGE_SWEEP", List.of("ES", "NQ", "YM", "RTY")));
	private final List<RuleResult> results = new ArrayList<>();

	@BeforeEach
	void subscribe() {
		rule.subscribe("test", results::add);
	}

	private static Instant t(String instant) {
		return Instant.parse(instant);
	}

	private static String plus(String instant, long seconds) {
		return t(instant).plusSeconds(seconds).toString();
	}

	private void range(String symbol) {
		range(symbol, DAY);
	}

	private void range(String symbol, LocalDate day) {
		String[] r = RANGES.get(symbol);
		rule.onEvent(new RangeCaptured(symbol, day, t("2026-10-12T14:07:30Z"), new BigDecimal(r[0]),
				new BigDecimal(r[1]), new BigDecimal(r[2])));
	}

	private void allRanges() {
		RANGES.keySet().forEach(this::range);
	}

	/** A bar that stays inside its range (no break), with the given low and high. */
	private void bar(String symbol, String instant, String low, String high) {
		bar(symbol, instant, low, high, false, false);
	}

	private void bar(String symbol, String instant, String low, String high, boolean brokeHigh, boolean brokeLow) {
		rule.onEvent(new BarPositioned(symbol, DAY, t(instant), new BigDecimal(high), new BigDecimal(low), brokeHigh,
				brokeLow));
	}

	private void ymBreaksLow(String instant) {
		bar("YM", instant, "44168.00", "44175.00", false, true);
	}

	private void ymBreaksHigh(String instant) {
		bar("YM", instant, "44235.00", "44245.00", true, false);
	}

	private List<RuleResult> verdicts() {
		return results.stream().filter(r -> r.kind() == Kind.RESULT).toList();
	}

	private List<String> buySymbols() {
		return verdicts().stream()
			.filter(r -> r.verdict() == Verdict.BULLISH)
			.map(r -> r.scope().getFirst())
			.sorted()
			.toList();
	}

	@Test
	void workedExampleTargetsBarsFirst() {
		allRanges();
		bar("ES", S, "6027.00", "6028.00");
		bar("NQ", S, "21108.00", "21110.00");
		bar("RTY", S, "2179.20", "2179.50");
		ymBreaksLow(S);

		assertThat(buySymbols()).containsExactly("ES", "RTY");
		RuleResult es = verdicts().stream().filter(r -> r.scope().equals(List.of("ES"))).findFirst().orElseThrow();
		assertThat(es.eventTime()).isEqualTo(t(S));
		assertThat(es.ruleKey()).isEqualTo("SEQUENCE_RANGE_SWEEP[ES,NQ,RTY,YM]");
		assertThat(es.evidence()).containsKeys("trigger", "target");
		assertThat(results).filteredOn(r -> r.kind() == Kind.REPORT)
			.singleElement()
			.satisfies(report -> {
				assertThat(report.code()).isEqualTo(SequenceRangeSweep.EXTREMITY_PASSED);
				assertThat(report.scope()).containsExactly("YM");
			});
	}

	@Test
	void workedExampleTriggerFirst() {
		allRanges();
		ymBreaksLow(S);
		assertThat(verdicts()).isEmpty();

		bar("ES", S, "6027.00", "6028.00");
		assertThat(buySymbols()).containsExactly("ES");
		bar("NQ", S, "21108.00", "21110.00");
		bar("RTY", S, "2179.20", "2179.50");

		assertThat(buySymbols()).containsExactly("ES", "RTY");
	}

	@Test
	void targetWithoutBarAtTheSecondUsesItsLatestBarBefore() {
		allRanges();
		bar("ES", plus(S, -1), "6027.00", "6028.00");
		ymBreaksLow(S);
		assertThat(verdicts()).isEmpty();

		bar("ES", plus(S, 1), "6031.00", "6031.50");

		assertThat(buySymbols()).containsExactly("ES");
	}

	@Test
	void windowCloseDecidesAPendingCheck() {
		allRanges();
		bar("RTY", plus(S, -2), "2179.20", "2179.50");
		ymBreaksLow(S);
		rule.onEvent(new WindowClosed("RTY", DAY, t("2026-10-12T14:30:00Z")));

		assertThat(buySymbols()).containsExactly("RTY");
	}

	@Test
	void undecidedCheckWaitsForTheTarget() {
		allRanges();
		ymBreaksLow(S);
		bar("NQ", plus(S, 5), "21095.00", "21100.00");

		assertThat(verdicts()).isEmpty();
	}

	@Test
	void highBreakGivesShortToTargetsInUpperHalf() {
		allRanges();
		ymBreaksHigh(S);
		bar("ES", S, "6029.50", "6030.00");
		bar("NQ", S, "21100.00", "21104.00");
		bar("RTY", S, "2183.00", "2185.00");

		assertThat(verdicts()).extracting(r -> r.scope().getFirst()).containsExactlyInAnyOrder("ES", "RTY");
		assertThat(verdicts()).allSatisfy(r -> assertThat(r.verdict()).isEqualTo(Verdict.BEARISH));
	}

	@Test
	void targetOutsideItsRangeDoesNotQualify() {
		allRanges();
		ymBreaksLow(S);
		bar("ES", S, "6025.00", "6028.00", false, true);
		bar("RTY", S, "2177.00", "2179.00", false, true);
		bar("NQ", S, "21108.00", "21110.00");

		assertThat(verdicts()).isEmpty();
		assertThat(results).filteredOn(r -> r.kind() == Kind.REPORT).hasSize(3);
	}

	@Test
	void oneResultPerTargetPerDay() {
		allRanges();
		bar("ES", S, "6027.00", "6028.00");
		ymBreaksLow(S);
		bar("ES", plus(S, 10), "6027.50", "6028.00");
		ymBreaksLow(plus(S, 10));

		assertThat(buySymbols()).containsExactly("ES");
	}

	@Test
	void triggersAreHeldUntilEveryRangeIsKnown() {
		range("ES");
		range("NQ");
		range("YM");
		bar("ES", S, "6027.00", "6028.00");
		ymBreaksLow(S);
		assertThat(verdicts()).isEmpty();
		assertThat(rule.dayStatus()).isEqualTo(DayStatus.UNKNOWN);

		range("RTY");
		assertThat(rule.dayStatus()).isEqualTo(DayStatus.VALID);
		assertThat(buySymbols()).containsExactly("ES");

		bar("RTY", S, "2179.20", "2179.50");
		assertThat(buySymbols()).containsExactly("ES", "RTY");
	}

	@Test
	void attachingAfterTheRangeWasCapturedInvalidatesTheDay() {
		range("NQ");
		range("YM");
		range("RTY");
		bar("ES", S, "6027.00", "6028.00");
		ymBreaksLow(S);

		assertThat(rule.dayStatus()).isEqualTo(DayStatus.INVALID_WINDOW);
		assertThat(verdicts()).isEmpty();
		assertThat(results).filteredOn(r -> r.kind() == Kind.STATUS)
			.singleElement()
			.satisfies(status -> {
				assertThat(status.code()).isEqualTo(SequenceRangeSweep.INVALID_WINDOW);
				assertThat(status.scope()).containsExactly("ES");
			});
	}

	@Test
	void unavailableRangeInvalidatesTheDay() {
		range("ES");
		range("NQ");
		range("YM");
		rule.onEvent(new RangeUnavailable("RTY", DAY, t("2026-10-12T14:07:30Z"), Reason.LATE_START));

		assertThat(rule.dayStatus()).isEqualTo(DayStatus.INVALID_WINDOW);
		bar("ES", S, "6027.00", "6028.00");
		ymBreaksLow(S);
		assertThat(verdicts()).isEmpty();
	}

	@Test
	void nextTradingDayStartsFresh() {
		range("ES");
		rule.onEvent(new RangeUnavailable("RTY", DAY, t("2026-10-12T14:07:30Z"), Reason.LATE_START));
		range("NQ");
		range("YM");
		assertThat(rule.dayStatus()).isEqualTo(DayStatus.INVALID_WINDOW);

		LocalDate next = DAY.plusDays(1);
		range("ES", next);
		assertThat(rule.dayStatus()).isEqualTo(DayStatus.UNKNOWN);
		range("NQ", next);
		range("YM", next);
		range("RTY", next);
		assertThat(rule.dayStatus()).isEqualTo(DayStatus.VALID);
	}
}

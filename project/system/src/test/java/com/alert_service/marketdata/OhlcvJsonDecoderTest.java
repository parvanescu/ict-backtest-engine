package com.alert_service.marketdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

class OhlcvJsonDecoderTest {

	private static final Topic NQ = new Topic(DataType.OHLCV, "NQ");

	private final OhlcvJsonDecoder decoder = new OhlcvJsonDecoder(JsonMapper.builder().build());

	private static byte[] json(String json) {
		return json.getBytes(StandardCharsets.UTF_8);
	}

	@Test
	void decodesContractExampleWithExactPrices() {
		OhlcvBar bar = decoder.decode(NQ, json("""
				{"symbol":"NQ","startTime":1760133600000000000,"open":25012.25,"high":25013.00,
				 "low":25011.75,"close":25012.75,"volume":42}"""));

		assertThat(bar.symbol()).isEqualTo("NQ");
		assertThat(bar.startTime()).isEqualTo(1_760_133_600_000_000_000L);
		assertThat(bar.high()).isEqualTo(new BigDecimal("25013.00"));
		assertThat(bar.low()).isEqualTo(new BigDecimal("25011.75"));
		assertThat(bar.volume()).isEqualTo(42);
	}

	@Test
	void ignoresUnknownFields() {
		OhlcvBar bar = decoder.decode(NQ, json("""
				{"symbol":"NQ","granularity":"S1","startTime":1,"open":1,"high":2,"low":1,"close":2,"volume":0}"""));

		assertThat(bar.startTime()).isEqualTo(1);
	}

	@Test
	void rejectsSymbolNotMatchingTopic() {
		assertThatThrownBy(() -> decoder.decode(NQ, json("""
				{"symbol":"ES","startTime":1,"open":1,"high":2,"low":1,"close":2,"volume":0}""")))
			.isInstanceOf(InvalidMessageException.class)
			.hasMessageContaining("does not match");
	}

	@Test
	void rejectsMissingField() {
		assertThatThrownBy(() -> decoder.decode(NQ, json("""
				{"symbol":"NQ","startTime":1,"open":1,"high":2,"close":2,"volume":0}""")))
			.isInstanceOf(InvalidMessageException.class);
	}

	@Test
	void rejectsHighBelowLow() {
		assertThatThrownBy(() -> decoder.decode(NQ, json("""
				{"symbol":"NQ","startTime":1,"open":1,"high":1,"low":2,"close":1,"volume":0}""")))
			.isInstanceOf(InvalidMessageException.class);
	}

	@Test
	void rejectsMalformedJson() {
		assertThatThrownBy(() -> decoder.decode(NQ, json("{not json")))
			.isInstanceOf(InvalidMessageException.class);
	}
}

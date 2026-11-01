package com.alert_service.marketdata;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import org.springframework.stereotype.Component;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Decodes the JSON {@code BarEvent} of the market data contract. Unknown fields are ignored.
 */
@Component
public class OhlcvJsonDecoder implements MarketDataDecoder<OhlcvBar> {

	private final JsonMapper mapper;

	public OhlcvJsonDecoder(JsonMapper mapper) {
		this.mapper = mapper;
	}

	@Override
	public DataType dataType() {
		return DataType.OHLCV;
	}

	@Override
	public OhlcvBar decode(Topic topic, byte[] payload) {
		BarEvent event;
		try {
			event = mapper.readValue(payload, BarEvent.class);
		}
		catch (JacksonException ex) {
			throw new InvalidMessageException("Malformed bar JSON: " + ex.getOriginalMessage(), ex);
		}
		if (event.symbol == null || event.startTime == null || event.open == null || event.high == null
				|| event.low == null || event.close == null || event.volume == null) {
			throw new InvalidMessageException("Bar is missing a required field");
		}
		if (!event.symbol.equals(topic.symbol())) {
			throw new InvalidMessageException("Bar symbol " + event.symbol + " does not match topic " + topic);
		}
		try {
			return new OhlcvBar(event.symbol, event.startTime, event.open, event.high, event.low, event.close,
					event.volume);
		}
		catch (IllegalArgumentException ex) {
			throw new InvalidMessageException(ex.getMessage(), ex);
		}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record BarEvent(String symbol, Long startTime, BigDecimal open, BigDecimal high, BigDecimal low,
			BigDecimal close, Long volume) {
	}
}

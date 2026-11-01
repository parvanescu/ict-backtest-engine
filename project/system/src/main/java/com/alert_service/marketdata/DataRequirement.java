package com.alert_service.marketdata;

/**
 * The static part of what a basic rule consumes. The request supplies the symbol.
 */
public record DataRequirement(DataType dataType) {

	public Topic topicFor(String symbol) {
		return new Topic(dataType, symbol);
	}
}

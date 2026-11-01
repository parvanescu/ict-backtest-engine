package com.alert_service.marketdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class TopicTest {

	@Test
	void valueIsDataTypeDotSymbol() {
		assertThat(new Topic(DataType.OHLCV, "ES").value()).isEqualTo("OHLCV.ES");
		assertThat(new DataRequirement(DataType.OHLCV).topicFor("RTY").value()).isEqualTo("OHLCV.RTY");
	}

	@Test
	void rejectsInvalidSymbols() {
		assertThatIllegalArgumentException().isThrownBy(() -> new Topic(DataType.OHLCV, "es"));
		assertThatIllegalArgumentException().isThrownBy(() -> new Topic(DataType.OHLCV, "E.S"));
		assertThatIllegalArgumentException().isThrownBy(() -> new Topic(DataType.OHLCV, ""));
		assertThatIllegalArgumentException().isThrownBy(() -> new Topic(DataType.OHLCV, null));
	}
}

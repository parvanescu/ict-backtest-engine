package com.alert_service.api;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param allowedOriginPatterns browser origins allowed to open the WebSocket (the React UI)
 * @param heartbeat             STOMP heartbeat interval in both directions; a silent client is disconnected
 */
@ConfigurationProperties("alert.api")
public record ApiProperties(
		@DefaultValue({ "http://localhost:*", "http://127.0.0.1:*" }) List<String> allowedOriginPatterns,
		@DefaultValue("10s") Duration heartbeat) {
}

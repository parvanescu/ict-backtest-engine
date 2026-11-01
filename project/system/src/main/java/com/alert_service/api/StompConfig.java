package com.alert_service.api;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP over WebSocket at {@code /ws}, one connection per client. Clients only subscribe: a subscription to
 * {@code /topic/bars/{symbol}} or {@code /topic/signals/{ruleKey}} starts that feed or rule
 * ({@link SubscriptionRouter}). Spring's in-memory broker delivers each message to every session subscribed to its
 * topic; it can later be replaced by an external one (relay mode).
 */
@Configuration
@EnableWebSocketMessageBroker
public class StompConfig implements WebSocketMessageBrokerConfigurer {

	public static final String ENDPOINT = "/ws";
	public static final String BARS_TOPIC = "/topic/bars/";
	public static final String SIGNALS_TOPIC = "/topic/signals/";

	private final ApiProperties properties;
	private final SubscriptionRouter router;

	public StompConfig(ApiProperties properties, SubscriptionRouter router) {
		this.properties = properties;
		this.router = router;
	}

	/** Frames of one session are handled in the order sent. */
	@Override
	public void registerStompEndpoints(StompEndpointRegistry registry) {
		registry.addEndpoint(ENDPOINT).setAllowedOriginPatterns(properties.allowedOriginPatterns().toArray(String[]::new));
		registry.setPreserveReceiveOrder(true);
	}

	@Override
	public void configureClientInboundChannel(ChannelRegistration registration) {
		registration.interceptors(router);
	}

	/** Messages reach each session in the order they were published. */
	@Override
	public void configureMessageBroker(MessageBrokerRegistry registry) {
		long heartbeat = properties.heartbeat().toMillis();
		registry.setPreservePublishOrder(true);
		registry.enableSimpleBroker("/topic")
			.setHeartbeatValue(new long[] { heartbeat, heartbeat })
			.setTaskScheduler(heartbeatScheduler());
	}

	@Bean
	ThreadPoolTaskScheduler heartbeatScheduler() {
		ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
		scheduler.setPoolSize(1);
		scheduler.setThreadNamePrefix("stomp-heartbeat-");
		return scheduler;
	}
}

package com.alert_service.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.broker.AbstractBrokerMessageHandler;
import org.springframework.messaging.support.ExecutorChannelInterceptor;
import org.springframework.stereotype.Component;

import com.alert_service.bars.BarFeedService;
import com.alert_service.bars.BarMessage;
import com.alert_service.run.RunService;
import com.alert_service.run.SessionSubscriptions.Ref;
import com.alert_service.signal.Signal;

/**
 * Turns client subscriptions into bar feeds and rule runs.
 * <p>
 * It acts after the broker has handled the frame, so the subscription is already registered: an error published to
 * the subscribed topic reaches the client. Invalid subscriptions get an {@code ERROR} message on that same topic.
 * Dependencies are looked up lazily because this interceptor is part of the broker configuration they depend on.
 */
@Component
public class SubscriptionRouter implements ExecutorChannelInterceptor {

	private static final Logger log = LoggerFactory.getLogger(SubscriptionRouter.class);

	private final ObjectProvider<RunService> runs;
	private final ObjectProvider<BarFeedService> bars;
	private final ObjectProvider<SimpMessagingTemplate> messaging;

	public SubscriptionRouter(ObjectProvider<RunService> runs, ObjectProvider<BarFeedService> bars,
			ObjectProvider<SimpMessagingTemplate> messaging) {
		this.runs = runs;
		this.bars = bars;
		this.messaging = messaging;
	}

	@Override
	public void afterMessageHandled(Message<?> message, MessageChannel channel, MessageHandler handler,
			Exception ex) {
		if (!(handler instanceof AbstractBrokerMessageHandler) || ex != null) {
			return;
		}
		SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.wrap(message);
		SimpMessageType type = accessor.getMessageType();
		if (type != SimpMessageType.SUBSCRIBE && type != SimpMessageType.UNSUBSCRIBE) {
			return;
		}
		Ref ref = new Ref(accessor.getSessionId(), accessor.getSubscriptionId());
		if (type == SimpMessageType.UNSUBSCRIBE) {
			runs.getObject().unsubscribe(ref);
			bars.getObject().unwatch(ref);
		}
		else {
			subscribe(ref, accessor.getDestination());
		}
	}

	private void subscribe(Ref ref, String destination) {
		if (destination == null) {
			return;
		}
		if (destination.startsWith(StompConfig.BARS_TOPIC)) {
			String symbol = destination.substring(StompConfig.BARS_TOPIC.length());
			try {
				bars.getObject().watch(ref, symbol);
			}
			catch (IllegalArgumentException invalid) {
				send(destination, BarMessage.error(invalid.getMessage()));
			}
			catch (RuntimeException failure) {
				log.error("Bar feed for {} failed to start", symbol, failure);
				send(destination, BarMessage.error("Internal error: " + failure.getClass().getSimpleName()));
			}
		}
		else if (destination.startsWith(StompConfig.SIGNALS_TOPIC)) {
			String ruleKey = destination.substring(StompConfig.SIGNALS_TOPIC.length());
			try {
				runs.getObject().subscribe(ref, ruleKey);
			}
			catch (IllegalArgumentException invalid) {
				send(destination, Signal.error(ruleKey, Signal.INVALID_SUBSCRIPTION, invalid.getMessage()));
			}
			catch (RuntimeException failure) {
				log.error("Rule {} failed to start", ruleKey, failure);
				send(destination, Signal.error(ruleKey, Signal.INTERNAL_ERROR,
						"Internal error: " + failure.getClass().getSimpleName()));
			}
		}
	}

	private void send(String destination, Object payload) {
		messaging.getObject().convertAndSend(destination, payload);
	}
}

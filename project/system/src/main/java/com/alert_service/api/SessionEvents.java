package com.alert_service.api;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import com.alert_service.bars.BarFeedService;
import com.alert_service.run.RunService;

/**
 * A user is one STOMP session. When it ends (closed tab, or missed heartbeats), its rule runs and bar feeds end too.
 */
@Component
public class SessionEvents {

	private final RunService runs;
	private final BarFeedService bars;

	public SessionEvents(RunService runs, BarFeedService bars) {
		this.runs = runs;
		this.bars = bars;
	}

	@EventListener
	public void onDisconnect(SessionDisconnectEvent event) {
		runs.unsubscribeAll(event.getSessionId());
		bars.unwatchAll(event.getSessionId());
	}
}

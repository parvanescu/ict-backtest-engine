package com.alert_service.marketdata.zmq;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param endpoints      adapter PUB endpoints every SUB socket connects to, e.g. {@code tcp://127.0.0.1:5556}
 * @param receiveTimeout how long a provider blocks on its socket before checking whether it was closed
 */
@ConfigurationProperties("alert.zmq")
public record ZmqProperties(@DefaultValue("tcp://127.0.0.1:5556") List<String> endpoints,
		@DefaultValue("200ms") Duration receiveTimeout) {
}

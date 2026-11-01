package com.alert_service;

import java.util.List;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.alert_service.api.ApiProperties;
import com.alert_service.marketdata.MarketDataDecoder;
import com.alert_service.marketdata.zmq.ProviderManager;
import com.alert_service.marketdata.zmq.ZmqDataProviderFactory;
import com.alert_service.marketdata.zmq.ZmqProperties;
import com.alert_service.rule.AggregateRuleFactory;
import com.alert_service.rule.BasicRuleFactory;
import com.alert_service.rule.RuleRegistry;
import com.alert_service.timegrid.TimeGrid;

/**
 * Wires the framework-free core: time grid, ZeroMQ providers and the rule registry.
 */
@Configuration
@EnableConfigurationProperties({ ZmqProperties.class, ApiProperties.class })
public class AlertServiceConfiguration {

	@Bean
	TimeGrid timeGrid() {
		return TimeGrid.cme();
	}

	@Bean
	ZmqDataProviderFactory dataProviderFactory(ZmqProperties properties) {
		return new ZmqDataProviderFactory(properties);
	}

	@Bean
	ProviderManager providerManager(ZmqDataProviderFactory factory, List<MarketDataDecoder<?>> decoders) {
		return new ProviderManager(factory, decoders);
	}

	@Bean
	RuleRegistry ruleRegistry(List<BasicRuleFactory> basicRules, List<AggregateRuleFactory> aggregateRules,
			ProviderManager providers) {
		return new RuleRegistry(basicRules, aggregateRules, providers);
	}
}

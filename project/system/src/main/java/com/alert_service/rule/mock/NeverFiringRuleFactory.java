package com.alert_service.rule.mock;

import java.util.List;

import org.springframework.stereotype.Component;

import com.alert_service.rule.AggregateRuleDescriptor;
import com.alert_service.rule.AggregateRuleFactory;
import com.alert_service.rule.RuleKey;

@Component
public class NeverFiringRuleFactory implements AggregateRuleFactory {

	@Override
	public AggregateRuleDescriptor descriptor() {
		return NeverFiringRule.DESCRIPTOR;
	}

	@Override
	public NeverFiringRule create(RuleKey key, List<String> symbols) {
		return new NeverFiringRule(key);
	}
}

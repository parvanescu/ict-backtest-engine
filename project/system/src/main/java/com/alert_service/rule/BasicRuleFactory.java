package com.alert_service.rule;

public interface BasicRuleFactory {

	BasicRuleDescriptor descriptor();

	BasicRule<?, ?> create(RuleKey key, String symbol);
}

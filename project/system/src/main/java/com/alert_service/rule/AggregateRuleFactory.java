package com.alert_service.rule;

import java.util.List;

public interface AggregateRuleFactory {

	AggregateRuleDescriptor descriptor();

	AggregateRule<?, ?> create(RuleKey key, List<String> symbols);
}

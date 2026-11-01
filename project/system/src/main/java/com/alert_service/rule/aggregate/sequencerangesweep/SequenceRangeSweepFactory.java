package com.alert_service.rule.aggregate.sequencerangesweep;

import java.util.List;

import org.springframework.stereotype.Component;

import com.alert_service.rule.AggregateRuleDescriptor;
import com.alert_service.rule.AggregateRuleFactory;
import com.alert_service.rule.RuleKey;

@Component
public class SequenceRangeSweepFactory implements AggregateRuleFactory {

	@Override
	public AggregateRuleDescriptor descriptor() {
		return SequenceRangeSweep.DESCRIPTOR;
	}

	@Override
	public SequenceRangeSweep create(RuleKey key, List<String> symbols) {
		return new SequenceRangeSweep(key);
	}
}

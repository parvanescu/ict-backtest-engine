package com.alert_service.rule.basic.rangebreak;

import org.springframework.stereotype.Component;

import com.alert_service.rule.BasicRuleDescriptor;
import com.alert_service.rule.BasicRuleFactory;
import com.alert_service.rule.RuleKey;
import com.alert_service.timegrid.TimeGrid;

@Component
public class RangeBreakWatchFactory implements BasicRuleFactory {

	private final TimeGrid grid;

	public RangeBreakWatchFactory(TimeGrid grid) {
		this.grid = grid;
	}

	@Override
	public BasicRuleDescriptor descriptor() {
		return RangeBreakWatch.DESCRIPTOR;
	}

	@Override
	public RangeBreakWatch create(RuleKey key, String symbol) {
		return new RangeBreakWatch(key, symbol, grid);
	}
}

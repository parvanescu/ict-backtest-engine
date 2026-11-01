package com.alert_service.demo;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;

/**
 * A scripted stretch of market data for the simulator: it plays from the start of 3-3-2 to the end of 3-4-1 (see
 * {@link ScenarioPlayer}) and, at the listed New York times, places some instruments' bars against their own 3-3-3
 * range. Every other second the instruments walk inside their ranges until 3-3-4 ends.
 *
 * @param id          short name used on the command line and in the page
 * @param title       one line for the page
 * @param description what the scenario shows, as facts
 * @param triggers    the scripted seconds, in time order
 * @param expected    the range sweep events the rule is expected to report, one line each (display text)
 */
public record Scenario(String id, String title, String description, List<Trigger> triggers, List<String> expected) {

	/** How an instrument's bar is placed against its own 3-3-3 range at a scripted second. */
	public enum Shape {
		/** Wick below the range low, close back at the mean. */
		SWEEP_LOW,
		/** Wick above the range high, close back at the mean. */
		SWEEP_HIGH,
		/** Whole bar in the lower half, low between L and M. */
		LOWER_HALF,
		/** Whole bar in the upper half, high between M and H. */
		UPPER_HALF
	}

	/** One scripted second (New York time) and the shape of each instrument's bar in it. */
	public record Trigger(LocalTime time, Map<String, Shape> shapes) {
	}
}

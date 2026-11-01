package com.alert_service.demo;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.alert_service.demo.Scenario.Shape;
import com.alert_service.demo.Scenario.Trigger;

/** The scenarios the simulator offers. Each lists every instrument at its scripted seconds. */
public final class Scenarios {

	private Scenarios() {
	}

	public static final List<Scenario> ALL = List.of(
			new Scenario("low-sweep", "Low break, partial agreement",
					"YM wicks below its 3-3-3 low. ES and RTY have a low in the lower half of their own ranges; "
							+ "NQ's low is in the upper half.",
					List.of(new Trigger(LocalTime.of(10, 12, 40), Map.of("YM", Shape.SWEEP_LOW, "ES", Shape.LOWER_HALF,
							"RTY", Shape.LOWER_HALF, "NQ", Shape.UPPER_HALF))),
					List.of("10:12:40 ES: YM broke its low; ES low is between L and M",
							"10:12:40 RTY: YM broke its low; RTY low is between L and M",
							"NQ: no event, its low is above M")),
			new Scenario("high-sweep", "High break, partial agreement",
					"NQ wicks above its 3-3-3 high. YM and RTY have a high in the upper half of their own ranges; "
							+ "ES's high is in the lower half.",
					List.of(new Trigger(LocalTime.of(10, 15, 20), Map.of("NQ", Shape.SWEEP_HIGH, "YM",
							Shape.UPPER_HALF, "RTY", Shape.UPPER_HALF, "ES", Shape.LOWER_HALF))),
					List.of("10:15:20 YM: NQ broke its high; YM high is between M and H",
							"10:15:20 RTY: NQ broke its high; RTY high is between M and H",
							"ES: no event, its high is below M")),
			new Scenario("no-event", "Breaks that qualify nobody",
					"Four breaks and no target: the others are on the wrong side of their means (10:10:00, 10:20:00), "
							+ "two instruments break in the same second so neither is a target (10:25:00), and a break "
							+ "after 3-3-4 has ended (10:40:00) is outside the watch.",
					List.of(new Trigger(LocalTime.of(10, 10, 0), Map.of("NQ", Shape.SWEEP_LOW, "ES", Shape.UPPER_HALF,
							"YM", Shape.UPPER_HALF, "RTY", Shape.UPPER_HALF)),
							new Trigger(LocalTime.of(10, 20, 0), Map.of("YM", Shape.SWEEP_HIGH, "ES", Shape.LOWER_HALF,
									"NQ", Shape.LOWER_HALF, "RTY", Shape.LOWER_HALF)),
							new Trigger(LocalTime.of(10, 25, 0), Map.of("ES", Shape.SWEEP_LOW, "YM", Shape.SWEEP_LOW,
									"NQ", Shape.UPPER_HALF, "RTY", Shape.UPPER_HALF)),
							new Trigger(LocalTime.of(10, 40, 0), Map.of("YM", Shape.SWEEP_LOW, "ES", Shape.LOWER_HALF,
									"RTY", Shape.LOWER_HALF, "NQ", Shape.LOWER_HALF))),
					List.of("No range sweep events: only range break reports")),
			new Scenario("repeat-triggers", "One event per target per day",
					"YM breaks its low (ES and RTY get events), later NQ breaks its high. YM gets its first event; "
							+ "ES and RTY also qualify again but already have today's event.",
					List.of(new Trigger(LocalTime.of(10, 12, 40), Map.of("YM", Shape.SWEEP_LOW, "ES", Shape.LOWER_HALF,
							"RTY", Shape.LOWER_HALF, "NQ", Shape.UPPER_HALF)),
							new Trigger(LocalTime.of(10, 20, 15), Map.of("NQ", Shape.SWEEP_HIGH, "YM", Shape.UPPER_HALF,
									"ES", Shape.UPPER_HALF, "RTY", Shape.UPPER_HALF))),
					List.of("10:12:40 ES and RTY: YM broke its low",
							"10:20:15 YM: NQ broke its high; YM high is between M and H",
							"10:20:15 ES and RTY: no second event, they already have today's")));

	public static Optional<Scenario> byId(String id) {
		return ALL.stream().filter(scenario -> scenario.id().equals(id)).findFirst();
	}
}

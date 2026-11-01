---
tracker_id: ""
id: 1
type: story
title: "Show which instruments a range break checked, and which did not qualify"
parent: none
covers: []
sources: []
companions: []
after: []
assignee: ""
refined: true
hitl: false
risk: medium
---

# Show which instruments a range break checked, and which did not qualify

## Description

When an instrument breaks its 3-3-3 range, the rule checks every other instrument once, at the break's second. Until now only the instruments that qualified were reported. The service now also reports each instrument that was checked and did not qualify, and the UI shows that result per instrument, so every instrument a break looked at is visible with its range.

## Requirements

1. **Checked, no event is reported**
   **Given** all four instruments have a frozen range
   **When** YM breaks its low and NQ's bar at that second has a low outside `[L, M)`
   **Then** a `REPORT` `RANGE_SWEEP_NO_EVENT` is published for NQ, with the trigger (symbol, side, price) and NQ's bar time, low and `H`, `L`, `M`, and ES and RTY still get `RANGE_SWEEP_EVENT`.

2. **Only real checks are reported**
   **Given** a target that is already signalled, has no bar up to the second, or broke its own range at that second
   **Then** no `RANGE_SWEEP_NO_EVENT` is published for it, and none is published on an invalid day.

3. **Header shows the latest check result**
   **Given** an instrument has a check result
   **Then** its panel header shows "Sweep event at HH:MM:SS" for an event, or an outlined "Checked, no event at HH:MM:SS" for a no-event result, in New York time.

4. **Reports window states the facts**
   **Then** the instrument's reports window lists the no-event result as, e.g., "YM broke 3-3-3 low 44168; NQ low 21108 outside L 21090 / M 21105". For a high break it reads "outside M / H".

5. **Clears only on reset**
   **When** the user stops and restarts the feed, or the connection is re-established
   **Then** the results are gone until new signals arrive. Nothing else, including 10:30 NY, clears them.

6. **Neutral wording**
   **Then** every line states computed facts only, with no directive wording.

## Boundaries

- Must not change: which targets qualify, the once-per-target-per-day rule, `RANGE_SWEEP_EVENT` and `EXTREMITY_PASSED` content.
- The new report adds one signal per checked, non-qualifying target, so signal ids after it shift by one in a run that has one.
- Update `06_sequence_range_sweep_design.md`, `07_signals_and_ui.md` and `11_ui.md` in the same PR.

## References

- rule — documents/implementation_strategy/rules/sequence_range_sweep.md
- architecture — documents/architecture/06_sequence_range_sweep_design.md, Output
- ui — documents/architecture/11_ui.md

## Notes

- Decision (2026-11-01): the user first asked for a highlight of the instruments still to be targeted, shrinking from 3 to 2 to 1. The rule has no standing watch: each break checks the other instruments once, at its own second (SequenceRangeSweep.resolvePending/test). The user chose to report each break's check result instead, through an additive backend signal.
- Decision (2026-11-01): the result clears only on UI Stop/Start or reconnect.
- Decision (2026-11-01): no UX design. The layout is the builder's choice, within neutral wording.
- Not built: a forward-looking highlight of unsignalled instruments (UI only), offered as the alternative and not chosen.

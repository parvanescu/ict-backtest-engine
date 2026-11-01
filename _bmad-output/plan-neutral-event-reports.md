---
title: 'Neutral event reports for Sequence range sweep'
type: 'feature'
ticket: ''
created: '2026-11-01'
status: 'built'
baseline_revision: 'b923a95c5c2a979b01d64d1d533da88dae6ebf0b'
route: 'full'
route_source: 'auto'
risk: 'medium'
review: 'quick'
review_source: 'pinned'
lenses_ran: ['quick']
review_loop_iteration: 0
context: ['{project-root}/AGENTS.md']
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The Sequence range sweep emits a `RESULT` with a `BULLISH`/`BEARISH` verdict (docs say BUY/SHORT), and the UI colours and labels it that way. That reads as investment advice and breaks the project policy of computed facts only.

**Approach:** Drop the verdict. When the rule fires for an instrument, emit a `REPORT` with code `RANGE_SWEEP_EVENT` scoped to that instrument, stating facts only (which instrument broke which side, at what price, and the target's own bar against its H/M/L). The UI shows it as a report for that instrument, with neutral markers and no directional colours or arrows.

## Boundaries & Constraints

**Always:** Wording is computed facts ("YM broke its 3-3-3 low at 44168; ES low 6027.25 is between L 6026.00 and M 6029.00"). Rule logic (which targets qualify, once per target per day) is unchanged. Update the matching notes in `documents/` in the same PR.

**Never:** Change the rule's qualification logic or the theory. Touch other rules' notes (`sweep`, `return_confirmation`, `ssmt`, `strategy_philosophy`, `03_cycle_lifecycle`): they are unimplemented methodology specs. No order, account or advice wording anywhere.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Low break | YM breaks low; ES low in [L, M) | `REPORT` `RANGE_SWEEP_EVENT`, scope `[ES]`, evidence trigger/target as today; no `verdict` field | none |
| High break | YM breaks high; ES high in (M, H] | Same, evidence `side: HIGH` | none |
| Not qualifying / already signalled | target outside its half, or already reported today | No event | none |
| History call | Client loads `/api/signals` | Events appear as `REPORT`; day status becomes VALID on the first event | none |

</frozen-after-approval>

## Code Map

- `project/system/.../rule/Verdict.java` -- delete (only user is this rule).
- `project/system/.../rule/RuleResult.java` -- remove `Kind.RESULT`, `verdict` field and `verdict(...)` factory.
- `.../sequencerangesweep/SequenceRangeSweep.java:190-212` -- `test()` emits `RuleResult.report(key, RANGE_SWEEP_EVENT, LEVEL, List.of(target), second, evidence)`; add `RANGE_SWEEP_EVENT` constant; fix class javadoc (BUY/SHORT).
- `.../signal/Signal.java` -- drop `verdict` component and mapping; fix javadoc.
- Tests: `SequenceRangeSweepTest` (`verdicts()` filters `Kind.RESULT`, asserts BULLISH/BEARISH), `StompEndToEndTest:125`, `demo/DemoAdapter` (BULLISH/BEARISH text).
- `project/ui/src/api/types.ts` -- remove `Verdict`, `verdict`, `'RESULT'` kind.
- `project/ui/src/lib/signals.ts` -- `dayStatusAfter` and `rangeLevelsFor` key on code `RANGE_SWEEP_EVENT`; `markersFor` emits one neutral circle marker (text `Sweep`) at the event second; `latestResultFor` becomes latest event; `reportsFor` includes both codes.
- `project/ui/src/components/InstrumentPanel.tsx` -- neutral badge "Sweep event at HH:MM:SS".
- `project/ui/src/components/ReportsFeed.tsx` -- `ReportRow` renders by `code`: `EXTREMITY_PASSED` as today, `RANGE_SWEEP_EVENT` as a fact line (trigger symbol, side, price; target low/high vs L–M–H).
- `project/ui/src/theme.ts` -- remove `bullish`/`bearish`; marker uses `muted`/`close`.
- `project/ui/src/lib/signals.test.ts` -- rewrite fixtures/tests to events.
- Docs: `architecture/04_rule_model.md`, `06_sequence_range_sweep_design.md`, `07_signals_and_ui.md`, `10_live_bars.md`, `11_ui.md`; `implementation_strategy/rules/sequence_range_sweep.md` and `00_rules_engine.md` (generic output table); README.MD if it mentions verdicts.

## Tasks & Acceptance

**Execution:**
- [ ] Backend: remove Verdict/RESULT, emit `RANGE_SWEEP_EVENT` report -- neutral wire model
- [ ] Backend tests + `DemoAdapter` text updated -- keep suite and demo green
- [ ] UI: types, signals lib, panel, feed, theme, tests -- neutral display per instrument
- [ ] Docs updated as listed -- `documents/` is the spec

**Acceptance Criteria:**
- Given any code path, when searching the repo (excluding the untouched methodology notes), then `BULLISH`, `BEARISH`, `BUY`, `SHORT`, `verdict` and directional colours are absent.
- Given an event fires for ES, when the UI renders, then ES shows the event in its Reports feed and a neutral marker; other instruments show nothing.
- Given the demo adapter runs, when the sweep fires, then the UI shows the events with factual text only.

## Implementation Notes

## Plan Change Log

## Review Triage Log

Quick lens, 8 findings: 3 medium/low patched, 5 rejected.

- patch (medium): `00_rules_engine.md` and `05_alerts_and_open_questions.md` still labelled the sweep BUY / SHORT -- fixed.
- patch (low): `04_rule_model.md:82` said NEVER_FIRING never emits a `RESULT` -- now `REPORT`; "STATUS result" wording in 04 and 06 simplified.
- patch (low): `11_ui.md` ASCII box misaligned -- realigned.
- rejected, low: marker text `Sweep event` vs plan's `Sweep`, only `close` colour used -- Code Map detail, behaviour matches intent.
- rejected, low: `ReportRow` falls back to the extremity row for unknown codes; `EventRow` side default -- unreachable with current backend output, fix adds branches.
- left: generic `verdict` row and "Comparable verdicts" in `00_rules_engine.md` describe unimplemented rules (out of scope per the plan).

## Design Notes

Wire change is breaking (`kind: RESULT`/`verdict` removed); acceptable, consumers are the bundled UI only (private, non-commercial).

## Verification

**Commands:**
- `cd project/system && ./gradlew test` -- expected: BUILD SUCCESSFUL
- `cd project/ui && npm test && npm run build` -- expected: tests pass, build succeeds
- `grep -rniE "bullish|bearish|\bBUY\b|\bSHORT\b|verdict" project/system/src project/ui/src documents/architecture` -- expected: no matches

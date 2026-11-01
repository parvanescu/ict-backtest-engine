---
id: 1
type: initiative
title: "Try the Sequence range sweep on focused scenarios and a clean slate"
parent: none
sources: []
companions: []
after: []
refined: false
risk: low
---

# Try the Sequence range sweep on focused scenarios and a clean slate

## Description

The demo adapter plays a whole trading day (09:44 to 17:00 New York) with two scripted events. Only the span from 3-3-2 to the end of 3-4-1 can trigger the Sequence range sweep, so most of the day is dead time, and there is one scenario. This initiative narrows the simulator to that span, adds scenarios that show what the rule does and does not report, gives the simulator its own page to run them, and gives the main UI a button to wipe the day's data between runs.

## Outcome

A developer picks a scenario in the simulator page, watches the rule report the expected events in the main UI within minutes, presses Reset data, and runs another scenario without restarting anything.

## Requirements

R1: A scenario plays bars only from the start of 3-3-2 (09:22:30 New York) to the end of 3-4-1 (10:52:30 New York).
R2: Four scenarios exist: low-side break, high-side break, breaks that yield no event, and repeated triggers (one event per target per day). Each lists the events it expects.
R3: The simulator serves a web page to pick a scenario and speed, play it and stop it.
R4: The main UI has a Reset data button. It clears the service's stored bars, signals and rule state and the UI's charts and reports, so the next scenario needs no restart.

## Constraints

- The simulator stays test tooling that publishes bars over ZeroMQ; the service gains no data provider (AGENTS.md Policy).
- Reset is data deletion on a service with no authentication: it is reachable from the local origins already allowed, no more (09_build_and_run, Configuration).
- Texts stay computed facts, never directive wording (AGENTS.md Policy).
- Rules keep using bar timestamps only; core classes stay framework-free (AGENTS.md Conventions).
- `documents/` is updated in the same PR as the behavior (AGENTS.md Conventions).

## Done when

- The simulator plays the four scenarios, each only inside 09:22:30 to 10:52:30.
- Each scenario's listed events appear in the main UI and in `/api/signals`, and nothing else is reported.
- After Reset data, `/api/bars` and `/api/signals` are empty and the UI shows empty charts and reports.
- `docker compose up --build` runs it all; `./gradlew test` and `npm test` pass.

## Boundaries

- Out: new rules, the other theory rules, real data providers, other time windows or days, a scenario editor.
- Touch point: service API and bar store — reset endpoint; owner: epic-simulator-scenarios
- Must not change: the rule's qualification logic, signal wording, the data contract.

## References

- architecture — documents/architecture/09_build_and_run.md, Gradle and Docker
- architecture — documents/architecture/11_ui.md, Run it
- rule — documents/implementation_strategy/rules/sequence_range_sweep.md, Algorithm and Timeline

## Notes

- Decision: 2026-11-01 — window ends at the end of 3-4-1, 10:52:30 (user).
- Decision: 2026-11-01 — scenarios run from a page served by the simulator, not the main UI (user).
- Decision: 2026-11-01 — Reset data clears the service and the UI (user).
- Decision: 2026-11-01 — one epic, three stories, delivered in one PR (user).

---
id: 1
type: epic
title: "Run focused scenarios and reset between them"
parent: initiative-simulator-scenarios
covers: [R1, R2, R3, R4]
sources: []
companions: []
after: []
refined: true
risk: low
---

# Run focused scenarios and reset between them

## Description

Three stories deliver the initiative: the reset (service endpoint and UI button) first, since it is the smallest path through service and UI; then the scenario engine with the narrowed window and four scenarios; then the simulator page that runs them. One PR.

## Outcome

A developer runs a scenario, checks the events, resets, and runs another without restarting the service.

## Requirements

R1 (parent R1): Scenarios play only 09:22:30 to 10:52:30 New York.
R2 (parent R2): Four scenarios, each with its expected events listed.
R3 (parent R3): A simulator web page picks scenario and speed, plays and stops.
R4 (parent R4): Reset data clears service and UI state.

## Done when

- Stories 1 to 3 are done and their checks pass.
- With `docker compose up --build`, each scenario run from the simulator page produces exactly its listed events, and Reset data between runs leaves no trace of the previous one.

## Boundaries

- Out: anything in the initiative's Out list.

## References

- parent — _bmad-output/initiative-simulator-scenarios/initiative-simulator-scenarios.md, Requirements

## Notes

- Decision: 2026-11-01 — tracer bullet is story 1, reset (thinnest path through service and UI).
- Decision: 2026-11-01 — no closing refactor sweep or end-to-end suite; the user wants one PR and the change is small (assumed, say if you want one).

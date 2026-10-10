# Implementation Strategy: Overview

Concrete scope of what this project implements from [[strategy_philosophy]].

## The idea: a rules engine on top of the quarterly theory

The **framework** is the quarterly theory: a fixed UTC time grid (6h, 90-min, 22.5-min windows) and the values captured in it ([[03_cycle_lifecycle]]). On top of it the service runs independent **rules**, each a separate time-based criterion, one file each in the `rules` folder. When several rules agree, the evidence is stronger.

| Rule | Note |
|------|------|
| Sweep | [[sweep]] |
| Return confirmation | [[return_confirmation]] |
| Sequence range sweep | [[sequence_range_sweep]] |

How rules are combined: [[00_rules_engine]].

## Scope (phase 1)

| Item | Decision |
|------|----------|
| Instruments | ES, NQ, YM, RTY (CME equity index futures) |
| Daily quarters handled | Q1 Asia, Q2 London, Q3 NY AM |
| Daily quarter Q4 (NY PM) | Added later |
| Subdivisions | 6-hour session → 90-min → 22.5-min |
| Time | **UTC only**; fixed grid anchored at 22:00 UTC, every day is exactly 24 h |
| Output | Alerts (see [[05_alerts_and_open_questions]]) |

## Reading order

1. [[01_instruments_and_data]] - what we watch and what data we need
2. [[02_time_model]] - sessions and how they are subdivided
3. [[03_cycle_lifecycle]] - the framework: windows, captured values, nesting
4. [[04_ssmt]] - cross-instrument divergence
5. [[05_alerts_and_open_questions]] - what gets emitted, and what is still undecided
6. [[06_market_data_contract]] - ZeroMQ input, bar granularity, approximation policy
7. [[00_rules_engine]] - the rules engine, and the `rules` folder with one file per criterion

## High-level flow

```mermaid
flowchart LR
    A[MD adapter<br/>ES NQ YM RTY<br/>via ZeroMQ] --> B[Aggregator<br/>S1 / M1 bars]
    B --> C[Time model<br/>session, 90m, 22.5m]
    C --> D[Cycle framework<br/>captured values per window]
    D --> E[Rules engine<br/>sweep, return, sequence range sweep]
    E --> F[Alert engine]
    F --> G[Notification channel]
```

## Fractal levels in scope

```mermaid
flowchart TD
    D["Daily cycle (24h, from 22:00 UTC)"] --> S1["Q1 Asia 22:00-04:00 UTC"]
    D --> S2["Q2 London 04:00-10:00 UTC"]
    D --> S3["Q3 NY AM 10:00-16:00 UTC"]
    D -.-> S4["Q4 NY PM 16:00-22:00 UTC<br/>(later)"]
    S1 --> M["4 x 90-min quarters"]
    S2 --> M
    S3 --> M
    M --> Q["4 x 22.5-min quarters each"]
```

Each session is itself a full 4-quarter cycle (90-min quarters), and each 90-min quarter is a full cycle (22.5-min quarters).

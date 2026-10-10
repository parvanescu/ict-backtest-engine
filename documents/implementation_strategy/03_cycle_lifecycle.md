# Cycle Framework

Part of [[00_overview]]. This note describes the quarterly-theory framework the service runs: which windows exist, what values are captured in them, and how levels nest. The criteria that read these values live in [[00_rules_engine]] (one file per rule in the `rules` folder).

Time model: [[02_time_model]]. Theory: [[strategy_philosophy]].

## What is a "cycle" here

A cycle is any window that has four quarters: a **session** (quarters = 90m) or a **90-min quarter** (quarters = 22.5m). The same logic runs on both levels, **independently per instrument**.

The daily cycle's own quarters are the sessions themselves. At the daily level there is no "previous session" inside the same day for Q1 Asia, so the daily level is mostly **context** (see below).

## Values the framework captures

The framework only captures numbers. Rules decide what they mean.

| Value | Captured when | Used by |
|-------|---------------|---------|
| `q1High`, `q1Low`, `q1Open` | During and at end of Q1 of a cycle | [[sweep]], [[return_confirmation]] |
| `trueOpen` (first price of Q2) | Start of Q2 | [[return_confirmation]] |
| Bars of Q2, as they arrive | During Q2 | [[sweep]], [[return_confirmation]] |
| High, low and mean (`H`, `L`, `M`) of a given 22.5-min block | During the block | [[sequence_range_sweep]] |

```mermaid
flowchart LR
    B[Bar arrives] --> W[Place in window<br/>session / 90m / 22.5m]
    W --> U[Update captured values<br/>high, low, open of each window]
    U --> R[Notify rules]
```

## Nesting: levels reinforce each other

Cycles are nested, and a lower-level result is stronger when it agrees with the level above it.

```mermaid
flowchart TD
    A["Session cycle<br/>(e.g. NY AM, from its 90m quarters)"] --> B["90m cycle<br/>(e.g. 90m Q2 inside NY AM)"]
    B --> C["22.5m cycle<br/>(entry-level timing)"]
    A -. context .-> B
    B -. context .-> C
```

Example: a bullish result on a 90-min cycle that lies inside a NY AM session which is also bullish is higher confidence than the same result against the session's direction. How levels are prioritised is an open question in [[00_rules_engine]].

## Cross-session context (UTC)

| Session | UTC | Context it carries |
|---------|-----|--------------------|
| Asia (Q1) | 22:00-04:00 | Its high/low become the day's first range |
| London (Q2) | 04:00-10:00 | Expected to take out Asia's high or low |
| NY AM (Q3) | 10:00-16:00 | Expected to move opposite to London's sweep |

```mermaid
sequenceDiagram
    participant Asia as Asia (Q1)
    participant London as London (Q2)
    participant NY as NY AM (Q3)
    Asia->>London: range high / low
    London->>London: sweeps Asia high or low
    London->>NY: day direction = opposite of sweep
    NY->>NY: expansion
```

The sweep, return and bias logic that used to be described here is now in [[sweep]] and [[return_confirmation]].

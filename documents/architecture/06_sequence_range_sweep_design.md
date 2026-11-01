# Sequence Range Sweep: Design

Part of [[00_architecture_overview]]. The rule itself: [[sequence_range_sweep]]. Model: [[04_rule_model]].

As implemented in `rule.basic.rangebreak` and `rule.aggregate.sequencerangesweep`.

## Split

| Part | Instance | Job |
|------|----------|-----|
| Basic | `RANGE_BREAK_WATCH[symbol]`, one per symbol | Capture `H`, `L`, `M` of 3-3-3 for its instrument; during 3-3-4, report every bar's position against them and every break |
| Aggregate | `SEQUENCE_RANGE_SWEEP[symbols]` | Combine them: on a break by `X`, test every other `Y` at the same second and emit an event report for each target that qualifies |

Windows ([[01_time_grid]]): 3-3-3 = `L3` block 42 (09:45:00-10:07:30 NY), 3-3-4 = `L3` block 43 (10:07:30-10:30:00 NY).

## Basic rule: RANGE_BREAK_WATCH

| State | Meaning |
|-------|---------|
| `runningHigh`, `runningLow` | Running extremes during 3-3-3 |
| `high`, `low`, `mean` | `H`, `L`, `M`, frozen at the first bar of 3-3-4. `M = (H + L) / 2`, exact decimal |
| `phase` | `WAITING`, `CAPTURING`, `WATCHING`, `DONE` |

Facts, in this order per trading day:

| Event emitted | When | Content |
|---------------|------|---------|
| `RangeCaptured` | First bar at or after 3-3-4 start | symbol, trading day, `H`, `L`, `M` |
| `RangeUnavailable` | Instead of `RangeCaptured`, when the rule has no range today | reason: `LATE_START` (first bar of the day at or after 09:45:00 NY) or `NO_BARS_IN_RANGE` |
| `BarPositioned` | Every bar in 3-3-4 | second `s`, bar `high`, `low`, `brokeHigh` (`high > H`), `brokeLow` (`low < L`) |
| `WindowClosed` | First bar at or after 3-3-4 end | - |

A break is a `BarPositioned` with a break flag set, so the aggregate receives one ordered stream per symbol. `earliestBlock` = block 42.

## Aggregate rule: SEQUENCE_RANGE_SWEEP

| State | Meaning |
|-------|---------|
| `ranges` | `RangeCaptured` per symbol |
| `missingRanges` | Symbols that will have no range today |
| `recent` | Per symbol, the last 64 `BarPositioned` events |
| `closed` | Symbols whose `WindowClosed` arrived |
| `pending` | Checks (trigger, side, target) not decided yet |
| `signalled` | Targets that already got an event today (one per target per day) |

### Judging the day

Each symbol's stream is ordered, but streams of different symbols arrive in any order: the first 3-3-4 bar of ES can arrive before NQ's `RangeCaptured`. So the day is judged only once the range status of **every** symbol is known:

| A symbol's range status is known when | Status |
|---------------------------------------|--------|
| `RangeCaptured` arrived | captured |
| `RangeUnavailable` arrived | missing (basic rule started late, or no bars) |
| `BarPositioned` or `WindowClosed` arrived without a `RangeCaptured` before it | missing (this aggregate attached after the range was captured) |

All captured → day `VALID`. Any missing → `STATUS` `INVALID_WINDOW` naming the symbols, and nothing else that day. Breaks seen before the day is judged are held as pending checks and evaluated as soon as it is `VALID`.

### Same-second resolution, event time only

When `X` breaks at second `s`, a check is added for every other `Y`. A check is decided by `Y`'s own stream:

```mermaid
flowchart TD
    T["X breaks at second s"] --> Y{"Y has a bar at s<br/>in recent?"}
    Y -- "yes, and it broke itself" --> X["no event: Y is a trigger, not a target"]
    Y -- "yes" --> E["test Y with that bar"]
    Y -- no --> L{"Y's latest bar is after s,<br/>or Y's window closed?"}
    L -- yes --> K["test Y with its latest bar before s<br/>(no bar before s: no event)"]
    L -- "no (Y's bar for s may still come)" --> P["stay pending, re-check on every event"]
```

Test (from [[sequence_range_sweep]]): low break → an event for `Y` if `Y.L <= low < Y.M`; high break → an event if `Y.M < high <= Y.H`. A target already in `signalled` is skipped.

No timeout and no wall clock: every decision is driven by a later event, so replay and live behave the same.

Known limit: "one event per target per day" keeps the first check decided, not necessarily the earliest second. If `X`'s stream lags, a trigger by `Z` at a later second can be decided first. This needs two qualifying triggers within the cross-symbol delay (milliseconds), so it is accepted for iteration 1 ([[08_architecture_open_questions]]).

### Output

| Kind | When |
|------|------|
| `REPORT` `RANGE_SWEEP_EVENT` | A target qualifies, `scope` = the target; `eventTime` = trigger second `s`; evidence: trigger symbol, side, price; target bar time, low or high, `H`, `L`, `M` |
| `REPORT` `EXTREMITY_PASSED` | Every break, including ones that produce no event |
| `STATUS` `INVALID_WINDOW` | Some range is missing for the trading day |

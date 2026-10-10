# Daye's Quarterly Theory

> Learning document. Describes the strategy conceptually before any implementation.
> All times are **UTC**, always. No local time zones and no daylight saving. The theory is traditionally described in New York time; this project instead uses a fixed UTC grid (see [[02_time_model]]).
> Treat the exact boundaries below as the working definition for this project; verify against your own source material and adjust.

> Implementation scope for this project: [[00_overview]]

## 1. Core idea

Price delivery is **fractal and cyclical**. Any period of time, at any scale, can be split into **4 equal quarters**. Each quarter has a typical role in the lifecycle of a move:

| Quarter | Role | Typical behavior |
|---------|------|------------------|
| **Q1** | Accumulation | Establishes a range; sets the initial high/low and the opening reference. |
| **Q2** | Manipulation | False move (Judas swing / liquidity sweep) against the intended direction, usually taking out Q1's high or low. |
| **Q3** | Distribution | The real, expansive move in the true direction. |
| **Q4** | Continuation / Reversal | Either continues the Q3 move or reverses it, and seeds the next cycle's Q1. |

The model is the same as AMD (Accumulation → Manipulation → Distribution), with Q4 added as the continuation/reversal phase.

## 2. Fractal time cycles

The same 4-quarter structure repeats across scales. Each cycle's quarters are made of smaller cycles.

| Cycle | Duration | Q1 | Q2 | Q3 | Q4 |
|-------|----------|----|----|----|----|
| Yearly | 12 months | Jan–Mar | Apr–Jun | Jul–Sep | Oct–Dec |
| Monthly | ~4 weeks | Week 1 | Week 2 | Week 3 | Week 4 |
| Weekly | Mon–Thu (Fri = extension) | Monday | Tuesday | Wednesday | Thursday |
| Daily | 24 h (22:00–22:00 UTC) | 22:00–04:00 (Asia) | 04:00–10:00 (London) | 10:00–16:00 (NY AM) | 16:00–22:00 (NY PM) |
| 6-hour | 6 h | 1.5 h | 1.5 h | 1.5 h | 1.5 h |
| 90-minute | 90 min | 22.5 min | 22.5 min | 22.5 min | 22.5 min |

Notes:
- The trading day opens at the fixed **22:00 UTC** grid line, not at midnight. The session names are only labels for the four 6-hour quarters.
- A 90-minute cycle is the daily cycle scaled down: a day contains 16 of them, a 6-hour block contains 4.
- 90-minute cycles are anchored at 22:00 UTC: 22:00, 23:30, 01:00, 02:30, 04:00, 05:30, ...
- The scale you trade determines the scale you read. A Q3 on the daily cycle contains its own Q1–Q4 on the 90-minute cycle.

## 3. Key reference levels

- **Quarter open**: the opening price of each quarter.
- **True open**: the open of **Q2** of a cycle. It is the reference for the cycle's bias:
  - Price **above** the true open after manipulation → bullish bias for Q3.
  - Price **below** the true open after manipulation → bearish bias for Q3.
- **Q1 range**: high and low of Q1. These are the liquidity pools Q2 tends to sweep.
- **Previous quarter high/low**: liquidity targets at every scale.

## 4. How a cycle is expected to play out

1. **Q1** forms a range. Record its high, low and the open.
2. **Q2** opens (the true open is set). Price moves **away from the intended direction** and sweeps Q1's high or low (the manipulation).
   - Sweep of Q1 low, then reclaim → expect bullish Q3.
   - Sweep of Q1 high, then reject → expect bearish Q3.
3. **Q3** is the expansion in the opposite direction of the sweep, typically past the other side of the Q1 range.
4. **Q4** either continues the Q3 move (trend day) or reverses it (and often forms the Q1 of the next cycle).

The quarter being traded must be judged against the **higher-timeframe cycle** it sits inside (e.g. a 90-minute Q2 inside a daily Q3).

## 5. SSMT (Sequential SMT divergence)

Smart Money Technique divergence compares **correlated assets** (e.g. ES/NQ/YM, or EURUSD/GBPUSD/DXY inverse).

- **SMT**: one asset makes a new high/low while a correlated one fails to.
- **Sequential SMT (SSMT)**: the divergence is read **across quarters** instead of within a single candle. One asset sweeps the previous quarter's high/low while the other does not.

SSMT is used as **confirmation** that a manipulation phase is complete and the distribution phase is likely starting. It is most meaningful at the end of Q2 and into Q3.

## 6. Bias & signal summary

| Step | Condition | Meaning |
|------|-----------|---------|
| 1 | Quarter boundaries identified for the chosen cycle | Context |
| 2 | Q1 high/low and Q2 open (true open) recorded | Reference levels |
| 3 | Q2 sweeps a Q1 extreme | Manipulation in progress |
| 4 | Price returns across the true open / back inside Q1 range | Manipulation complete |
| 5 | (Optional) SSMT between correlated assets | Confirmation |
| 6 | Direction = opposite of the sweep | Q3 bias |

## 7. Open questions (to settle before implementation)

- Which cycles are in scope first (daily + 90-minute are the most common)?
- Which instruments (futures, forex, indices) and which correlated pairs for SSMT?
- Data source and timeframe (tick, 1m, 5m candles)?
- What does an "alert" mean: quarter start, true open set, sweep detected, SSMT detected, or bias confirmed?
- How strict is a "sweep" (wick beyond the level vs. candle close beyond)?
- How is Friday handled in the weekly cycle, and how are session/holiday gaps treated?

## 8. Disclaimer

This document is for learning purposes only and is not financial advice. The theory describes tendencies, not guarantees.

# Architecture: Open Questions

Part of [[00_architecture_overview]]. Business open questions stay in [[05_alerts_and_open_questions]] and in each rule note.

## Decided

| Question | Decision | Note |
|----------|----------|------|
| ~~STOMP or raw WebSocket~~ | STOMP | [[07_signals_and_ui]] |
| ~~How a trading day is named~~ | CME trade date (start date + 1 day) | [[01_time_grid]] |
| ~~Keep `spring-boot-starter-security`~~ | Removed for iteration 1, server bound to localhost | [[07_signals_and_ui]] |
| ~~Exact basic events of `RANGE_BREAK_WATCH`~~ | `RangeCaptured` / `RangeUnavailable`, `BarPositioned`, `WindowClosed` | [[06_sequence_range_sweep_design]] |
| ~~Size of the per-symbol `recent` buffer~~ | 64 bars | [[06_sequence_range_sweep_design]] |
| ~~ZeroMQ library, endpoint configuration~~ | JeroMQ 0.6.0; `alert.zmq.endpoints` | [[02_market_data_ingestion]], [[09_build_and_run]] |
| ~~Gap between the start reply and the subscription~~ | Gone: subscribing starts the rule, and the history call plus signal `id`s fill anything missed | [[07_signals_and_ui]] |

## Open

| Question | Note |
|----------|------|
| Bound on the aggregate queue, and what to do when full (unbounded now; volume is about one bar per second per symbol) | [[03_sources_and_subscriptions]] |
| "One event per target per day" keeps the first check decided, which is not always the earliest second when a symbol's stream lags; fixing it needs waiting for every stream to pass the second | [[06_sequence_range_sweep_design]] |
| An aggregate attached after 10:30 NY receives nothing that day, so its day status stays `UNKNOWN` instead of `INVALID_WINDOW` until the next day | [[06_sequence_range_sweep_design]] |

## Later

| Question | Note |
|----------|------|
| Holidays and early closes in the grid, including trade dates CME skips | [[01_time_grid]] |
| Late, duplicate, out-of-order bars | [[02_market_data_ingestion]] |
| Contract roll safety net inside providers | [[02_market_data_ingestion]] |
| Persistence of results and signals | [[07_signals_and_ui]] |
| Combining several aggregates into one signal | [[00_rules_engine]], [[07_signals_and_ui]] |
| Replay adapter for testing | [[01_instruments_and_data]] |
| Authentication, if the service ever runs on another machine than the UI | [[07_signals_and_ui]] |
| With an external broker (relay mode) a subscription is registered asynchronously, so an `ERROR` published right after it could be lost; check before switching | [[07_signals_and_ui]] |
| Bar history only covers bars received while a feed was open; storing bars read by rules too would need a hook into every provider | [[10_live_bars]] |
| Rules with parameters other than symbols would need them encoded in the topic name, or a command | [[07_signals_and_ui]] |

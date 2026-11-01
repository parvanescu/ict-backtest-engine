<!-- bmad:context -->
<!-- Verified 2026-11-01 against 7ba2cba. Managed by bmad-project-context; edits inside this block are replaced on refresh. Keep anything you want preserved outside the markers. -->

## alert-service

Java service (ZeroMQ market data in, STOMP/WebSocket signals out) plus a React UI, implementing Daye's Quarterly Theory rules over 1-second OHLCV bars. Specs and design notes are an Obsidian vault in `documents/`; code is in `project/system` (Gradle) and `project/ui` (Vite).

## Policy

- Never commit to `main`. One branch per change, open a PR, never merge it — the user squash-merges on GitHub.
- Never add, bundle, or integrate a market-data provider; the app only consumes what adapters publish. Sourcing data stays out of scope.
- Never add order routing, account access, or trade execution; the app is data-in, signals-out only.
- Write alert and UI text as computed facts ("Price 5142.25, above True Open 5138.50"), never directive wording ("consider long") — it keeps the app clear of investment-advice classification.
- Distribution is private and non-commercial. If a change implies public release or monetization, stop and ask the user.

## Where things are

- Business rules: `documents/implementation_strategy/00_overview.md`; one file per rule in `documents/implementation_strategy/rules/`.
- Service design: `documents/architecture/00_architecture_overview.md`. Build, configuration, endpoints, Docker: `09_build_and_run.md`. UI: `11_ui.md`.

## Running and verifying

- Gradle commands run from `project/system`, not the repo root. The full system runs with `docker compose up --build` from `project/`; details in `documents/architecture/09_build_and_run.md`.
- `.agents/` (installed skills) is gitignored. If it is missing, run `npx skills experimental_install` at the repo root to restore it from `skills-lock.json`.

## Conventions that differ from defaults

- `documents/` is the spec. When a note and the code disagree, ask the user which is right — never pick one. When a change alters behavior, update the matching note in the same PR.
- The theory is an external methodology, not our design: never treat business-logic questions about it as open decisions or redesign it.
- Rules use bar timestamps (UTC) only, never the wall clock; the grid is anchored at 18:00 New York.
- Core classes (`ProviderManager`, `RuleRegistry`, rules, `TimeGrid`) carry no Spring annotations; keep them framework-free so unit tests build them directly.

<!-- /bmad:context -->

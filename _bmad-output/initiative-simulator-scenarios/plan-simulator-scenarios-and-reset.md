# Simulator scenarios and data reset: Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Narrow the simulator to 3-3-2..3-4-1, ship four scenarios with a simulator web page to run them, and add a Reset data button backed by a service endpoint.

**Architecture:** The service gains `POST /api/reset` (clears `BarStore`, each `SignalStream`'s history and its rule instances). The UI calls it, then bumps a `resetEpoch` that makes the bar and signal hooks reload. The test-source demo adapter is split into scenario definitions, a deterministic bar generator (testable against the real rule, no ZeroMQ), a ZeroMQ engine on one thread, and a JDK `HttpServer` serving a plain HTML page.

**Tech Stack:** Java 25, Spring Boot 4.1.1, JUnit 5/AssertJ, JeroMQ, React 19 + Mantine + Vitest, JDK `com.sun.net.httpserver`.

**Spec:** `_bmad-output/initiative-simulator-scenarios/initiative-simulator-scenarios.md` (R1-R4) and the three entries in `tickets/epic-simulator-scenarios/tickets/tickets.toml`.

## Global Constraints

- Gradle commands run from `project/system`; UI commands from `project/ui`. Branch `feat/simulator-scenarios-and-reset`, never commit to `main`, open a PR, never merge.
- No data provider added to the service; the simulator stays under `src/test` (test tooling).
- Alert/UI/page text is computed facts, never directive wording.
- Rules use bar timestamps only; core classes (`ProviderManager`, `RuleRegistry`, rules, `TimeGrid`) stay free of Spring annotations.
- Update `documents/` notes in the same PR (`09_build_and_run.md`, `11_ui.md`, `07_signals_and_ui.md` if it lists endpoints).
- Window: first bar 09:22:30 New York, last bar 10:52:29 (end 10:52:30 exclusive), day 2026-10-12 (EDT), so 5,400 bars per instrument.

## Review Focus

- Reset while no rule runs or no bars stored: succeeds, returns empty state.
- Reset while the rule runs: rule keeps running, day status back to UNKNOWN, and bars played afterwards are judged fresh.
- Cross-site `POST /api/reset` from a page of another origin: must be refused (JSON-only, CORS-checked), since it deletes data.
- Play requested before the service subscribed to all four symbols: refused with a clear message, not silently dropping bars.
- Play while a scenario is playing, or Stop when idle: defined responses, no second player thread.

---

### Task 1: Service reset (story 1.1, back end)

**Files:**
- Modify: `project/system/src/main/java/com/alert_service/bars/BarStore.java` (add `clear()`)
- Modify: `project/system/src/main/java/com/alert_service/signal/SignalService.java` (add `reset()`, `SignalStream.reset()`)
- Create: `project/system/src/main/java/com/alert_service/api/ResetController.java`
- Modify: `project/system/src/main/java/com/alert_service/api/WebConfig.java` (allow `POST`)
- Test: `bars/BarStoreTest.java`, `signal/SignalServiceTest.java`, `api/ResetControllerTest.java` (new)

**Interfaces:**
- Produces: `void BarStore.clear()`; `synchronized void SignalService.reset()` (every attached stream drops its history and its aggregate, then re-subscribes to a fresh one; the stream object and its `ruleKey` stay the same, signal ids keep counting up); `POST /api/reset` (`consumes = "application/json"`) returning `204`.

- [ ] **Step 1: Failing tests.** `BarStoreTest.clearDropsAllBars`: append bars for ES and NQ, `clear()`, `since("ES", null)` has `tradingDay()==null` and no bars. `SignalServiceTest.resetClearsHistoryAndJudgesTheDayAgain`: attach, `playWorkedExample("2026-10-12")`, `take(3)`, `reset()`, `history(key.toString())` has empty `signals()` and `dayStatus()==UNKNOWN`; then play the worked example again and `take(3)` yields ids `4,5,6` (fresh rule fired again, ids not reused). `ResetControllerTest` (`@SpringBootTest(webEnvironment = RANDOM_PORT)`, `java.net.http.HttpClient`, same ZMQ-free config as `StompEndToEndTest`): `POST /api/reset` with `Content-Type: application/json` and body `{}` returns 204; the same POST with `Content-Type: text/plain` returns 415; a preflight from `Origin: http://evil.example` gets no `Access-Control-Allow-Origin`.
- [ ] **Step 2: Run** `./gradlew test --tests '*BarStoreTest' --tests '*SignalServiceTest' --tests '*ResetControllerTest'`. Expected: FAIL (compile errors, methods missing).
- [ ] **Step 3: Implement.** `BarStore.clear()` = `bySymbol.clear()`. `SignalStream.reset()`: under the stream's monitor clear `today` and `tradingDay`, close `attachment.subscription()` (last listener, so the registry tears the aggregate and basics down), then `attachment = registry.subscribe(key, SUBSCRIBER_ID, this::onResult)`; keep the `RuleKey` as a field to do so. `SignalService.reset()` iterates `streams.values()` calling it. `ResetController` (`@RestController`, `@PostMapping(value = "/api/reset", consumes = "application/json")`, `@ResponseStatus(NO_CONTENT)`) calls `bars.clear()` then `signals.reset()`. `WebConfig.allowedMethods("GET", "POST")`.
- [ ] **Step 4: Run** the same command. Expected: PASS.
- [ ] **Step 5: Commit** `feat: reset endpoint clears stored bars, signals and rule state`.

### Task 2: Reset button in the main UI (story 1.1, front end)

**Files:**
- Modify: `project/ui/src/api/history.ts` (add `resetData`), `src/hooks/useBarFeed.ts`, `src/hooks/useSignals.ts`, `src/App.tsx`, `src/components/ControlPanel.tsx`, `src/components/InstrumentPanel.tsx` (pass `resetEpoch` through)
- Test: `project/ui/src/api/history.test.ts` (new)

**Interfaces:**
- Consumes: `POST /api/reset` from Task 1.
- Produces: `resetData(): Promise<void>` (POST with `Content-Type: application/json`, body `{}`, throws `Error("POST /api/reset: HTTP <status>")` on non-2xx); `useBarFeed(symbol, sink, resetEpoch)` and `useSignals(ruleKey, resetEpoch)` include `resetEpoch: number` in their effect dependencies; `ControlPanel` props gain `onReset(): Promise<void>`.

- [ ] **Step 1: Failing test** `history.test.ts`: with `vi.stubGlobal('fetch', ...)`, `resetData()` calls `${HTTP_BASE}/api/reset` with method `POST`, header `Content-Type: application/json`; rejects with the message above on a 500.
- [ ] **Step 2: Run** `npm test -- history`. Expected: FAIL (`resetData` not exported).
- [ ] **Step 3: Implement.** `App` keeps `resetEpoch` state and `async function reset()`: `await resetData()`, then `setResetEpoch(n => n + 1)`; failures go to the existing `error` banner style (separate `resetError` state, red `Alert` titled "Reset failed"). `ControlPanel` shows a Mantine `Button` "Reset data" (variant `light`, color `gray`, `loading` while the call runs) under the Run switch, with a dimmed line "Clears the day's bars, signals and rule state." Update the existing explanatory text only if it contradicts.
- [ ] **Step 4: Run** `npm test` and `npm run build`. Expected: PASS, build type-checks.
- [ ] **Step 5: Commit** `feat(ui): Reset data button`.

### Task 3: Scenarios and generator (story 1.2)

**Files:**
- Create: `project/system/src/test/java/com/alert_service/demo/Scenario.java`, `Scenarios.java`, `ScenarioPlayer.java`
- Test: `project/system/src/test/java/com/alert_service/demo/ScenariosTest.java`

**Interfaces:**
- Produces: `record Scenario(String id, String title, String description, List<Trigger> triggers, List<String> expected)` where `record Trigger(LocalTime time, Map<String, Shape> shapes)` and `enum Shape { SWEEP_LOW, SWEEP_HIGH, LOWER_HALF, UPPER_HALF }` (moved from `DemoAdapter`; each trigger's `shapes` lists every instrument it scripts, unlisted ones walk inside their range); `Scenarios.ALL: List<Scenario>` and `Scenarios.byId(String): Optional<Scenario>`; `ScenarioPlayer.WINDOW_START = 09:22:30`, `WINDOW_END = 10:52:30`, `static void play(Scenario s, BarSink sink)` generating the bars second by second, instruments interleaved, deterministic (fixed `Random(42)`), where `interface BarSink { void accept(OhlcvBar bar) throws InterruptedException; }`. The player carries the existing walk/range/clamp logic from `DemoAdapter` (range captured 09:45:00-10:07:30, free walk outside 3-3-4, clamped inside it, scripted bars at trigger seconds).
- Scenario ids, triggers and `expected` strings (all times New York; ranges are each instrument's own 3-3-3):
  - `low-sweep` "Low break, partial agreement": 10:12:40 YM `SWEEP_LOW`, ES and RTY `LOWER_HALF`, NQ `UPPER_HALF`. Expected: events for ES and RTY (low break); none for NQ.
  - `high-sweep` "High break, partial agreement": 10:15:20 NQ `SWEEP_HIGH`, YM and RTY `UPPER_HALF`, ES `LOWER_HALF`. Expected: events for YM and RTY (high break); none for ES.
  - `no-event` "Breaks that qualify nobody": 10:10:00 NQ `SWEEP_LOW`, ES/YM/RTY `UPPER_HALF`; 10:20:00 YM `SWEEP_HIGH`, ES/NQ/RTY `LOWER_HALF`; 10:25:00 ES and YM both `SWEEP_LOW` with NQ and RTY `UPPER_HALF`; 10:40:00 (after 3-3-4) YM `SWEEP_LOW`, ES and RTY `LOWER_HALF`. Expected: no range sweep events at all (the 10:40 break is outside the watch).
  - `repeat-triggers` "One event per target per day": 10:12:40 YM `SWEEP_LOW`, ES/RTY `LOWER_HALF`, NQ `UPPER_HALF`; 10:20:15 NQ `SWEEP_HIGH`, YM `UPPER_HALF`, ES/RTY `UPPER_HALF`. Expected: ES and RTY at 10:12:40, YM at 10:20:15; ES and RTY qualify again at 10:20:15 but get no second event.

- [ ] **Step 1: Failing test** `ScenariosTest`: (a) `@ParameterizedTest @MethodSource` over `Scenarios.ALL`: feed `ScenarioPlayer.play` into the real rule (as `SignalServiceTest` does: `FakeDataProviderFactory`, `RuleRegistry`, `SignalService.attach`), then assert the set of `(RANGE_SWEEP_EVENT scope symbol, trigger time)` equals the scenario's expectation, which the test holds as data next to the ids above (the `expected` strings are display text only). (b) every generated bar for every scenario has `startTime` within `[WINDOW_START, WINDOW_END)` and each instrument gets exactly 5,400 bars. (c) `Scenarios.ALL` ids are unique and `byId("nope")` is empty.
- [ ] **Step 2: Run** `./gradlew test --tests '*ScenariosTest'`. Expected: FAIL (classes missing).
- [ ] **Step 3: Implement** the three classes per Interfaces. Do not touch `DemoAdapter` yet.
- [ ] **Step 4: Run** the same command. Expected: PASS for all four scenarios. If a scenario does not produce its expected events, fix the scripted shapes (the rule is not changed).
- [ ] **Step 5: Commit** `feat(demo): four scenarios over 3-3-2 to 3-4-1`.

### Task 4: Simulator engine, page and compose wiring (story 1.3)

**Files:**
- Create: `demo/Simulator.java` (ZeroMQ engine), `demo/SimulatorServer.java` (HTTP), `project/system/src/test/resources/simulator/index.html`
- Modify: `demo/DemoAdapter.java` (reduced to option parsing and wiring), `project/compose.yaml`, `project/system/Dockerfile`
- Test: `demo/SimulatorTest.java` (engine with a fake `BarSink` and fake subscription set), `demo/SimulatorServerTest.java` (HTTP on an ephemeral port)

**Interfaces:**
- Consumes: `Scenarios`, `ScenarioPlayer`, `BarSink` from Task 3.
- Produces: `Simulator` with `Status status()` (`record Status(String state, String scenario, String simulatedTime, int percent, Set<String> subscribed, Set<String> missing)`; `state` is `idle|playing|done`), `void play(String scenarioId, int speed)` throwing `IllegalStateException` with a user-facing message when already playing or when not all of ES, NQ, YM, RTY are subscribed, `IllegalArgumentException` for an unknown scenario or `speed < 1`; `void stop()` (no-op when idle). One thread owns the XPUB socket: it drains subscription frames and plays. `SimulatorServer(Simulator, host, port)` on `com.sun.net.httpserver`: `GET /` serves `simulator/index.html` from the classpath; `GET /api/scenarios` lists `{id,title,description,expected[]}`; `GET /api/status`; `POST /api/play` body `{"scenario":"low-sweep","speed":60}` returns 200, or 409 `{"error":...}` / 400 `{"error":...}`; `POST /api/stop` returns 200. The page: scenario select, speed input (default 60), Play and Stop buttons, the chosen scenario's description and expected events, status line polling every second ("Waiting for the service to subscribe to NQ, YM" / "Playing 10:12:40 NY, 38%" / "Done"). Text states facts only.
- CLI (`DemoAdapter`): `--endpoint`, `--speed`, `--control-host` (default `127.0.0.1`), `--control-port` (default `5557`), optional `--scenario <id>` which plays it as soon as all four symbols are subscribed. The old auto-play and its `--start-delay` option are removed.
- Compose: `demo-adapter` publishes `127.0.0.1:5557:5557` and its command adds `--control-host 0.0.0.0`; Dockerfile demo target `EXPOSE 5557`.

- [ ] **Step 1: Failing tests.** `SimulatorTest`: `play("low-sweep", 100000)` throws `IllegalStateException` while symbols are missing (message names them); once all four are marked subscribed it plays to `done` and the sink received 4 x 5,400 bars; a second `play` during playing throws; `stop()` ends playing early with state `idle`; unknown scenario throws `IllegalArgumentException`. `SimulatorServerTest`: `GET /api/scenarios` returns four entries; `POST /api/play` unknown id returns 400; with symbols missing returns 409 and a body naming them; `GET /` returns HTML containing "Play". (Simulator takes the bar output and the subscription source as constructor collaborators so tests need no ZeroMQ.)
- [ ] **Step 2: Run** `./gradlew test --tests '*SimulatorTest' --tests '*SimulatorServerTest'`. Expected: FAIL.
- [ ] **Step 3: Implement** the interfaces above; `DemoAdapter.main` builds the ZMQ socket-backed collaborators and starts the server. Pacing: sleep `1000 / speed` ms per simulated second (as today). Keep javadoc on `DemoAdapter` current.
- [ ] **Step 4: Run** the two test classes, then `./gradlew test` (full suite). Expected: PASS.
- [ ] **Step 5: Commit** `feat(demo): simulator page to run scenarios`.

### Task 5: Docs and end-to-end check

**Files:**
- Modify: `documents/architecture/09_build_and_run.md` (demoAdapter row and options, `POST /api/reset` in Endpoints, compose table gains port 5557, "Playing the demo day again" replaced by Reset data + Play), `documents/architecture/11_ui.md` (Rule panel row gains Reset data; "Run it" replaced by the scenario table and simulator page), `documents/architecture/07_signals_and_ui.md` only if it lists endpoints, `README.MD` only if it describes the demo run.

- [ ] **Step 1:** Update the notes so they match the code (no leftover mention of the single 09:44-17:00 day or `--start-delay`).
- [ ] **Step 2: Run end to end** without Docker: `./gradlew bootRun` and `npm run dev` and `./gradlew demoAdapter` (background). With `curl`: open the signals topic is a STOMP topic, so verify through the UI-less API: `GET /api/bars/ES`, then `POST` the simulator `/api/play` for each of the four scenarios (speed 600), `GET /api/signals/SEQUENCE_RANGE_SWEEP%5BES,NQ,RTY,YM%5D` after each, then `POST /api/reset` (JSON) and confirm bars and signals are empty. Expected: each scenario's events only; empty after reset. Note: the signal history exists only while a client runs the rule, so keep one STOMP client (the browser UI) open, or use `StompEndToEndTest`-style coverage if no browser is available, and say which was done.
- [ ] **Step 3: Run** `./gradlew test`, `npm test`, `npm run build` (from their folders). Expected: all PASS.
- [ ] **Step 4: Commit** `docs: simulator scenarios and reset`; push the branch and open the PR (never merge).

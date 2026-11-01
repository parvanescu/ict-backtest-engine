# Build and Run

Part of [[00_architecture_overview]]. The Gradle project is `project/system`; commands below run from there.

## Requirements

| Item | Version |
|------|---------|
| JDK | 25 (Gradle toolchain) |
| Gradle | wrapper, 9.7.1 (`./gradlew`) |
| Spring Boot | 4.1.1 |
| ZeroMQ | JeroMQ 0.6.0 (pure Java, no native library) |

## Gradle

| Command | Does |
|---------|------|
| `./gradlew test` | Unit tests plus the end-to-end test (STOMP client + ZeroMQ publisher on random local ports) |
| `./gradlew bootRun` | Runs the service on `127.0.0.1:8080` |
| `./gradlew bootJar` | Builds `build/libs/alert-service-0.0.1-SNAPSHOT.jar`; run it with `java -jar` |
| `./gradlew demoAdapter` | The market data simulator: binds `tcp://127.0.0.1:5556` and serves its page on `http://127.0.0.1:5557` to play scenarios ([[11_ui]], Simulator). Options (`--args="..."`): `--endpoint`, `--control-host` (default `127.0.0.1`), `--control-port` (5557), `--scenario <id>` to play one as soon as the service is subscribed (no page needed), `--speed` (simulated seconds per real second for `--scenario`, default 60) |

## Configuration

Every property can be set as an environment variable (Spring relaxed binding: `alert.zmq.endpoints` → `ALERT_ZMQ_ENDPOINTS`).

| Property | Default | Meaning |
|----------|---------|---------|
| `server.address` | `127.0.0.1` | Interface the HTTP/WebSocket server listens on. Localhost only, since nothing is authenticated |
| `server.port` | `8080` | |
| `alert.zmq.endpoints` | `tcp://127.0.0.1:5556` | Adapter PUB endpoints, comma-separated; every SUB socket connects to all of them |
| `alert.zmq.receive-timeout` | `200ms` | How often a provider checks whether it was closed |
| `alert.api.allowed-origin-patterns` | `http://localhost:*,http://127.0.0.1:*` | Browser origins allowed to open the WebSocket and call the history endpoints (the React dev server) |
| `alert.api.heartbeat` | `10s` | STOMP heartbeat; a client silent for longer is disconnected, and its rule runs and bar feeds end |

## Endpoints

| Endpoint | Use |
|----------|-----|
| `ws://127.0.0.1:8080/ws` | STOMP. Subscribe to `/topic/signals/{ruleKey}` and `/topic/bars/{symbol}` ([[07_signals_and_ui]], [[10_live_bars]]) |
| `GET /api/signals/{ruleKey}` | Day status and today's signals of a running rule (URL-encode the key) |
| `GET /api/bars/{symbol}?from=` | Today's stored 1-second bars |
| `POST /api/reset` | Clears the stored bars, the signal history and the state of running rules ([[07_signals_and_ui]]) |

## Docker (Docker Desktop)

`project/compose.yaml` runs the whole system:

```
cd project
docker compose up --build        # first time, or after code changes
docker compose up                # later starts
docker compose down              # stop and remove the containers
```

Then open `http://localhost:5173`.

| Container | Image (built from) | Published | Does |
|-----------|--------------------|-----------|------|
| `alert-service` | `project/system/Dockerfile`, default target | `127.0.0.1:8080` | The service. Listens on all interfaces inside the container (`SERVER_ADDRESS=0.0.0.0`); published on the host's loopback only, since nothing is authenticated |
| `ui` | `project/ui/Dockerfile` (Node build, served by nginx) | `127.0.0.1:5173` | The React UI. The browser runs on the host, so it is built to call the service at `127.0.0.1:8080` |
| `demo-adapter` | `project/system/Dockerfile`, target `demo` | `127.0.0.1:5557` | The simulator: market data on `tcp://demo-adapter:5556` (inside the compose network only) and its page on port 5557, published on the host's loopback only, since nothing is authenticated |

The service reads market data from `ALERT_ZMQ_ENDPOINTS`, `tcp://demo-adapter:5556` by default. With a real adapter running on the host instead (PowerShell):

```
$env:ALERT_ZMQ_ENDPOINTS="tcp://host.docker.internal:5556"; docker compose up --build alert-service ui
```

Playing another scenario: press Reset data in the UI, then Play in the simulator page. The service keeps the day's bars and rule state until reset, and a scenario replays the same day, so without a reset the old bars would stay on screen.

## Adapter side, for reference

An adapter binds a ZeroMQ PUB socket on the configured endpoint and sends two-frame messages: the topic (`OHLCV.ES`), then the JSON bar ([[06_market_data_contract]]). The service subscribes only while a rule needs the symbol.

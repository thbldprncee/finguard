# Performance tests

A k6 test suite for the main path, `POST /api/transactions` through nginx and the API
gateway, and the asynchronous pipeline behind it: transaction-service → outbox → Kafka →
fraud-detection-service → Kafka → transaction-service.

```bash
./loadtest/run.command                                  # smoke, load, stress, spike (~45 min)
PROFILES="smoke load" ./loadtest/run.command            # a subset
PROFILES="soak" SOAK_MIN=60 ./loadtest/run.command      # long test for leaks and drift
SKIP_BUILD=1 ./loadtest/run.command                     # reuse the built images
```

It needs only Docker and `.env`. k6 runs from its Docker image inside the compose network.
The script also starts the monitoring stack from `monitoring/compose.yml`. It creates a
temporary Keycloak client with password login (the frontend client keeps it disabled) and
test users `loadtest-NNNN@loadtest.local`. The temporary client is deleted afterwards.

## Test types

| Profile | Load | Question it answers |
|---------|------|---------------------|
| smoke | 5 rps, 30 s | Do the script, auth and pipeline work? The suite stops here on any error. |
| load | `LOAD_RPS` (100) for 10 min | Does the system meet the SLOs at the expected peak? |
| stress | +50 rps every 2 min, 100 → 600 | Where is the breaking point and what saturates first? Aborts once the system has clearly failed. |
| spike | 50 → `SPIKE_RPS` (400) in 10 s for 1 min → 50 | Does it survive a burst, and how fast does the backlog drain? |
| soak | `SOAK_RPS` (70) for `SOAK_MIN` (60) min | Do memory, tables or latency drift over time? |

The load model is open: k6 sends a fixed number of requests per second. A slow server
therefore shows up as latency and *dropped iterations*. With a closed model it would simply
receive fewer requests and look healthy.

## SLOs

| What | Objective |
|------|-----------|
| `POST /api/transactions` errors | < 0.1 % |
| `POST /api/transactions` latency | p99 < 300 ms |
| Fraud decision (POST → status leaves `PENDING`) | p95 < 3 s |

The decision latency is sampled at about 2 transactions per second at every load level.
That way the polling it needs does not become a load of its own.

## Monitoring

During the run, open Grafana at http://localhost:3001 (dashboard *FinGuard load test*).
Prometheus is at http://localhost:9090. Both are published on 127.0.0.1 only. The
services expose `/actuator/prometheus` inside the docker network.

| Area | Metrics |
|------|---------|
| RED per service | rate, errors and p99 latency |
| Kafka | consumer lag per group and topic; listener time |
| Saturation | DB pool (active / pending), Tomcat busy threads, process CPU, heap, GC time |
| PostgreSQL | connections and commits per database |

## Results

Results are written to `results/<timestamp>/` (gitignored):

| File | Content |
|------|---------|
| `<profile>-summary.txt` | Per-step table and SLO verdicts |
| `<profile>-steps.json` | The same data, machine-readable |
| `<profile>-k6-summary.json` | Full k6 summary |
| `<profile>-prometheus/*.json` | Prometheus series of the test window, including the drain |
| `samples.csv` | Every 5 s: CPU and memory per container, plus the pipeline backlog (pending transactions, unpublished outbox events) |
| `timeline.csv` | Start and end of each test, and how long the backlog took to drain |

## Caveat

The generator, the services, Kafka, PostgreSQL and Keycloak all share one laptop. The
numbers are therefore a lower bound and depend on the machine; k6's own CPU usage is part of
`samples.csv`. Compare runs on the same machine, for example before and after a change. Do
not quote these numbers as production capacity.

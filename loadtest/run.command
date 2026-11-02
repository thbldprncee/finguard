#!/bin/bash
# FinGuard performance test suite. Double-click in Finder or run ./loadtest/run.command
#
#   1. builds and starts the stack plus monitoring (Prometheus, Kafka/Postgres exporters,
#      Grafana on http://localhost:3001) and waits until everything answers;
#   2. creates a temporary Keycloak client with password login and USERS test users;
#      before each profile the fraud rules' activity history in Redis is reset;
#   3. runs the profiles one after another (default: smoke load stress spike). Before each
#      one it waits until the pipeline is idle, so tests do not influence each other;
#   4. records every 5 s CPU/memory per container and the pipeline backlog, and exports the
#      Prometheus metrics of each test window;
#   5. deletes the temporary client.
#
# Results: loadtest/results/<timestamp>/   (about 45 minutes for the default suite)
#
# Settings (environment variables):
#   PROFILES="smoke load stress spike"   add "soak" for the long test (SOAK_MIN, default 60)
#   USERS=2000  LOAD_RPS=100  START_RPS=100 STEP_RPS=50 MAX_RPS=600  SPIKE_RPS=400  SOAK_RPS=70
#   SKIP_BUILD=1   do not rebuild the images
set -u
cd "$(dirname "$0")/.."
ROOT="$(pwd)"

PROFILES="${PROFILES:-smoke load stress spike}"
USERS="${USERS:-2000}"   # one user per k6 VU, also the VU limit
K6_IMAGE="grafana/k6:0.54.0"
NETWORK="finguard_finguard-net"
KC="http://localhost:8180/auth"          # Keycloak admin API, published on 127.0.0.1 only
PROM="http://localhost:9090"
CLIENT_ID="finguard-loadtest"
COMPOSE="docker compose -f docker-compose.yml -f loadtest/monitoring/compose.yml"

RUN_DIR="$ROOT/loadtest/results/$(date +%Y%m%d-%H%M%S)"
mkdir -p "$RUN_DIR"
exec > >(tee -a "$RUN_DIR/run.log") 2>&1

say() { printf '\n=== %s  [%s]\n' "$*" "$(date +%H:%M:%S)"; }
fail() { printf '\nERROR: %s\n' "$*"; exit 1; }
http_code() { curl -s --max-time 5 -o /dev/null -w '%{http_code}' "$1"; }

say "FinGuard performance tests: ${PROFILES} (${USERS} users)"

# --- prerequisites & stack --------------------------------------------------------------
docker info >/dev/null 2>&1 || fail "Docker is not running. Start Docker Desktop and try again."
[ -f .env ] || fail ".env not found. Run scripts/generate-env.sh first."
set -a; . ./.env; set +a
[ -n "${KEYCLOAK_ADMIN_PASSWORD:-}" ] || fail "KEYCLOAK_ADMIN_PASSWORD is empty in .env"

if [ "${SKIP_BUILD:-0}" = "1" ]; then
  say "Starting the stack and monitoring (no rebuild)"
  $COMPOSE up -d || fail "docker compose up failed"
else
  say "Building and starting the stack and monitoring (the first build takes several minutes)"
  $COMPOSE up -d --build || fail "docker compose up --build failed"
fi

say "Waiting until Keycloak, the API and Prometheus answer"
for i in $(seq 1 120); do
  kc=$(http_code "$KC/realms/finguard/.well-known/openid-configuration")
  api=$(http_code "http://localhost:3000/api/accounts")
  prom=$(http_code "$PROM/-/ready")
  if [ "$kc" = "200" ] && [ "$api" = "401" ] && [ "$prom" = "200" ]; then echo "ready"; break; fi
  [ $((i % 6)) = 0 ] && echo "  still waiting: keycloak=$kc api=$api prometheus=$prom ($((i * 5 / 60)) min)"
  [ "$i" = "120" ] && { $COMPOSE ps; fail "stack not ready after 10 minutes"; }
  sleep 5
done

# --- Keycloak: temporary client + test users -------------------------------------------
. "$ROOT/loadtest/keycloak.sh"

say "Preparing Keycloak: client ${CLIENT_ID} and ${USERS} users"
create_client
ensure_users loadtest "$USERS"

# Keycloak ids of the test users: the fraud history reset below touches only their rows.
LOADTEST_IDS="$RUN_DIR/loadtest-user-ids.txt"
user_ids loadtest "$USERS" "$LOADTEST_IDS"

# --- sampler ------------------------------------------------------------------------------
psql_count() { # database query
  docker compose exec -T postgres psql -U postgres -d "$1" -tAc "$2" 2>/dev/null | tr -d '[:space:]'
}
pending() { psql_count transaction_db "select count(*) from txn.transactions where status = 'PENDING'"; }
txn_outbox() { psql_count transaction_db "select count(*) from txn.outbox_events where published_at is null"; }
fraud_outbox() { psql_count fraud_db "select count(*) from fraud.outbox_events where published_at is null"; }

sample_loop() {
  echo "epoch,time,container,cpu_percent,memory,pending_transactions,txn_outbox_backlog,fraud_outbox_backlog" > "$RUN_DIR/samples.csv"
  while :; do
    ep=$(date +%s); ts=$(date +%H:%M:%S)
    p=$(pending); a=$(txn_outbox); b=$(fraud_outbox)
    docker stats --no-stream --format '{{.Name}},{{.CPUPerc}},{{.MemUsage}}' 2>/dev/null \
      | grep '^finguard' | sed "s|^|$ep,$ts,|; s|\$|,$p,$a,$b|" >> "$RUN_DIR/samples.csv"
    sleep 5
  done
}

# Consumer lag over all service consumer groups (Kafka exporter via Prometheus). The SSE groups
# of the gateway are per instance and left behind by restarts, so they are not counted.
kafka_lag() {
  curl -s --max-time 5 -G "$PROM/api/v1/query" --data-urlencode \
    'query=sum(clamp_min(kafka_consumergroup_lag{consumergroup!~"api-gateway-sse.*",topic!~".*(retry|dlt).*"},0))' \
    | sed -nE 's/.*"value":\[[^,]+,"([0-9.e+]+)".*/\1/p' | cut -d. -f1
}

# Waits until no transaction waits for a decision, both outboxes are empty and no consumer lags
# behind (the audit backlog of one test kept loading PostgreSQL during the next one).
# Prints how many seconds that took.
wait_idle() {
  local start=$(date +%s)
  for i in $(seq 1 240); do
    p=$(pending); a=$(txn_outbox); b=$(fraud_outbox); l=$(kafka_lag)
    if [ "${p:-1}" = "0" ] && [ "${a:-1}" = "0" ] && [ "${b:-1}" = "0" ] && [ "${l:-1}" -lt 50 ]; then break; fi
    sleep 5
  done
  echo $(( $(date +%s) - start ))
}

# Exports the Prometheus series of one test window (5 s resolution).
export_prometheus() { # profile start end
  local out="$RUN_DIR/$1-prometheus"
  mkdir -p "$out"
  local HTTP='http_server_requests_seconds'
  local SEL='uri!~"/actuator.*"'
  q() { # name query
    curl -s --max-time 30 -G "$PROM/api/v1/query_range" --data-urlencode "query=$2" \
      --data-urlencode "start=$START" --data-urlencode "end=$END" --data-urlencode "step=5" > "$out/$1.json"
  }
  START=$2; END=$3
  q http_rps        "sum by (application) (rate(${HTTP}_count{$SEL}[30s]))"
  q http_p99        "histogram_quantile(0.99, sum by (le, application) (rate(${HTTP}_bucket{$SEL}[30s])))"
  q http_5xx        "sum by (application) (rate(${HTTP}_count{$SEL,outcome=\"SERVER_ERROR\"}[30s]))"
  q kafka_lag       "sum by (consumergroup, topic) (kafka_consumergroup_lag{topic!~\".*(retry|dlt).*\"})"
  q listener_p99    "histogram_quantile(0.99, sum by (le, application) (rate(spring_kafka_listener_seconds_bucket[30s])))"
  q listener_rps    "sum by (application) (rate(spring_kafka_listener_seconds_count[30s]))"
  q process_cpu     "process_cpu_usage"
  q system_cpus     "system_cpu_count"
  q hikari_active   "sum by (application) (hikaricp_connections_active)"
  q hikari_pending  "sum by (application) (hikaricp_connections_pending)"
  q hikari_max      "sum by (application) (hikaricp_connections_max)"
  q tomcat_busy     "sum by (application) (tomcat_threads_busy_threads)"
  q tomcat_max      "sum by (application) (tomcat_threads_config_max_threads)"
  q heap_used       "sum by (application) (jvm_memory_used_bytes{area=\"heap\"})"
  q heap_max        "sum by (application) (jvm_memory_max_bytes{area=\"heap\"})"
  q gc_time         "sum by (application) (rate(jvm_gc_pause_seconds_sum[30s]))"
  q pg_connections  "sum by (datname) (pg_stat_activity_count{datname=~\".*_db\"})"
  q pg_commits      "sum by (datname) (rate(pg_stat_database_xact_commit{datname=~\".*_db\"}[30s]))"
}

# --- run ----------------------------------------------------------------------------------
sample_loop &
SAMPLER=$!
trap 'kill $SAMPLER 2>/dev/null; delete_client' EXIT
echo "profile,k6_start,scenario_start,k6_end,idle_after_s,exit_code" > "$RUN_DIR/timeline.csv"

# Fraud history of the test users. Earlier runs leave thousands of payments per test user
# (far more than a real user makes) and, through REPEATED_BLOCKED, block every further
# transaction of an already blocked user for 24 h, so a run would start with a history and a
# decision mix no production user has. Reset before each profile:
#   Redis: amounts (STRUCTURING) and timestamps (VELOCITY); known devices and countries stay,
#          real users have them too;
#   fraud_db: the test users' fraud_analyses (REPEATED_BLOCKED reads them). Only rows of the
#          loadtest-* users are deleted; their transactions and audit records stay.
reset_fraud_history() {
  docker compose exec -T redis sh -c \
    'for p in "fraud:amounts:*" "fraud:velocity:*"; do redis-cli --scan --pattern "$p" | xargs -r -n 500 redis-cli unlink >/dev/null; done'
  { echo 'CREATE TEMP TABLE lt (id uuid);'
    echo 'COPY lt FROM STDIN;'
    cat "$LOADTEST_IDS"
    echo '\.'
    echo 'DELETE FROM fraud.fraud_analyses WHERE user_id IN (SELECT id FROM lt);'
  } | docker compose exec -T postgres psql -U postgres -d fraud_db -v ON_ERROR_STOP=1 -f - | grep -E "^DELETE"
}

for p in $PROFILES; do
  say "Waiting for an idle pipeline before '$p'"
  echo "idle after $(wait_idle) s"
  echo "fraud history of the test users reset: $(reset_fraud_history)"
  sleep 20

  say "Running '$p'"
  t0=$(date +%s)
  docker run --rm --name "finguard-k6-$p" --network "$NETWORK" \
    -e PROFILE="$p" -e BASE_URL=http://frontend:8080 -e USERS="$USERS" \
    -e LOAD_RPS="${LOAD_RPS:-100}" -e LOAD_MIN="${LOAD_MIN:-10}" \
    -e START_RPS="${START_RPS:-100}" -e STEP_RPS="${STEP_RPS:-50}" -e MAX_RPS="${MAX_RPS:-600}" \
    -e SPIKE_RPS="${SPIKE_RPS:-400}" -e SOAK_RPS="${SOAK_RPS:-70}" -e SOAK_MIN="${SOAK_MIN:-60}" \
    -e LOADTEST_PASSWORD="$LOADTEST_PASSWORD" \
    -v "$ROOT/loadtest:/scripts:ro" -v "$RUN_DIR:/results" \
    "$K6_IMAGE" run /scripts/finguard.js > "$RUN_DIR/$p-k6.log" 2>&1
  rc=$?
  t1=$(date +%s)
  cat "$RUN_DIR/$p-summary.txt" 2>/dev/null || tail -20 "$RUN_DIR/$p-k6.log"
  scenario_start=$(sed -nE 's/.*SCENARIO_START ([0-9]+).*/\1/p' "$RUN_DIR/$p-k6.log" | head -1)

  say "'$p' finished (k6 exit $rc); waiting for the backlog to drain"
  drain=$(wait_idle)
  echo "drained in $drain s"
  echo "$p,$t0,${scenario_start:-},$t1,$drain,$rc" >> "$RUN_DIR/timeline.csv"
  export_prometheus "$p" "$t0" "$((t1 + drain))"

  if [ "$p" = "smoke" ] && ! grep -q "rate<0.001 PASS" "$RUN_DIR/smoke-summary.txt" 2>/dev/null; then
    fail "smoke test failed - fix that before loading the system (see $RUN_DIR/smoke-k6.log)"
  fi
done

kill $SAMPLER 2>/dev/null
say "Done. Results: $RUN_DIR"
echo "Grafana dashboard: http://localhost:3001  (Prometheus: $PROM)"

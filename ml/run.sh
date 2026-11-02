#!/bin/bash
# ML tooling for the fraud model; runs in a container on the compose network, nothing to install.
#
#   ml/run.sh generate   synthetic labelled traffic through the running stack (USERS, DURATION_MIN,
#                        USER_PREFIX: a new prefix gives customers without earlier history)
#   ml/run.sh train      train on the labelled analyses and ship the model to fraud-detection-service
#
# The stack must be running (docker compose up -d). After "train", rebuild the service so it
# picks up the model: docker compose up -d --build fraud-detection-service
set -euo pipefail
cd "$(dirname "$0")/.."
ROOT="$(pwd)"

fail() { printf '\nERROR: %s\n' "$*"; exit 1; }
[ -f .env ] || fail ".env not found. Run scripts/generate-env.sh first."
set -a; . ./.env; set +a

IMAGE="finguard-ml"
NETWORK="finguard_finguard-net"
PG_DSN="postgresql://postgres:${POSTGRES_PASSWORD}@postgres:5432/fraud_db"
docker build -q -t "$IMAGE" ml >/dev/null

case "${1:-}" in
  generate)
    KC="http://localhost:8180/auth"
    CLIENT_ID="finguard-ml"
    USERS="${USERS:-1500}"
    USER_PREFIX="${USER_PREFIX:-mlgen}"
    . "$ROOT/loadtest/keycloak.sh"
    create_client
    trap delete_client EXIT
    ensure_users "$USER_PREFIX" "$USERS"
    docker run --rm --network "$NETWORK" -v "$ROOT/ml:/ml" \
      -e CLIENT_ID="$CLIENT_ID" -e LOADTEST_PASSWORD="$LOADTEST_PASSWORD" -e USERS="$USERS" -e USER_PREFIX="$USER_PREFIX" \
      -e DURATION_MIN="${DURATION_MIN:-30}" -e SEED="${SEED:-42}" -e PG_DSN="$PG_DSN" \
      "$IMAGE" python -u /ml/generate.py
    ;;
  train)
    docker run --rm --network "$NETWORK" -v "$ROOT:/repo" -e REPO=/repo -e PG_DSN="$PG_DSN" \
      "$IMAGE" python -u /repo/ml/train.py
    ;;
  *)
    echo "usage: ml/run.sh generate|train"; exit 2 ;;
esac

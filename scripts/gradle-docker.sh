#!/usr/bin/env bash
# Runs Gradle for one service inside a JDK 21 container (no local JDK 21 needed).
# Testcontainers works through the host Docker socket.
# Usage: scripts/gradle-docker.sh <service-dir> [gradle args...]   e.g. scripts/gradle-docker.sh auth-service test
set -euo pipefail
cd "$(dirname "$0")/.."
service="$1"; shift
exec docker run --rm \
  -v "$PWD/$service":/app -w /app \
  -v finguard-gradle-cache:/home/gradle/.gradle \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal \
  -e TESTCONTAINERS_RYUK_DISABLED=true \
  --add-host=host.docker.internal:host-gateway \
  gradle:8.8-jdk21-alpine gradle "$@" --no-daemon -q

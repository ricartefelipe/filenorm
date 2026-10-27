#!/usr/bin/env bash
# Roda a suíte da API contra um Postgres descartável (projeto compose isolado, dados em tmpfs).
# Nunca usa o banco de desenvolvimento (5434) nem o de produção.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PROJECT="filenorm-test"
COMPOSE=(docker compose -p "$PROJECT" -f "$ROOT/docker-compose.test.yml")

cleanup() { "${COMPOSE[@]}" down --remove-orphans >/dev/null 2>&1 || true; }
trap cleanup EXIT

"${COMPOSE[@]}" up -d --wait
cd "$ROOT/apps/api"
FILENORM_TEST_DB_URL="jdbc:postgresql://127.0.0.1:5433/filenorm" mvn -B test "$@"

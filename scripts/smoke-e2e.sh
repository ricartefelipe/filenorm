#!/usr/bin/env bash
# Jornada integrada contra a imagem e o Compose reais (docker-compose.e2e.yml):
# login por magic link com código de demo, API key, upload de OFX/CSV/CNAB, prova de fechamento,
# autorização negativa, limite de upload e backup -> restore em banco vazio, conferido pela API.
#
# Pré-requisitos: docker (com compose), curl, jq e o JAR já empacotado (cd apps/api && mvn -DskipTests package).
# Saída: 0 = sucesso; 1 = falha de comportamento; 2 = inconclusivo (ambiente: docker, porta, readiness).
# Só cria e remove os próprios projetos (filenorm-e2e, filenorm-e2e-restore) e seus volumes.
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
FILE="$ROOT/docker-compose.e2e.yml"
FIX="$ROOT/apps/api/src/test/resources/fixtures"
CODE="e2e-access-code"
PORT="${E2E_API_PORT:-18090}"
RESTORE_PORT="${E2E_RESTORE_PORT:-18091}"
WORK="$(mktemp -d)"
SRC=(docker compose -p filenorm-e2e -f "$FILE")
DST=(docker compose -p filenorm-e2e-restore -f "$FILE")

fail() { echo "FALHA: $*" >&2; exit 1; }
inconclusive() { echo "INCONCLUSIVO: $*" >&2; exit 2; }
cleanup() {
  "${SRC[@]}" down -v --remove-orphans >/dev/null 2>&1 || true
  E2E_API_PORT="$RESTORE_PORT" "${DST[@]}" down -v --remove-orphans >/dev/null 2>&1 || true
  rm -rf "$WORK"
}
trap cleanup EXIT

for tool in docker curl jq; do command -v "$tool" >/dev/null || inconclusive "$tool não encontrado"; done
docker info >/dev/null 2>&1 || inconclusive "docker indisponível"
ls "$ROOT"/apps/api/target/filenorm-api-*.jar >/dev/null 2>&1 || inconclusive "JAR ausente: rode mvn -DskipTests package em apps/api"

wait_ready() { # base timeout
  local base="$1" deadline=$((SECONDS + $2))
  until curl -sf "$base/actuator/health/readiness" >/dev/null; do
    (( SECONDS > deadline )) && return 1
    sleep 1
  done
}

wait_job() { # base key job -> status terminal
  local deadline=$((SECONDS + 60)) status
  while (( SECONDS < deadline )); do
    status=$(curl -sf "$1/v1/jobs/$3" -H "Authorization: Bearer $2" | jq -r .status)
    case "$status" in succeeded|partial|failed) echo "$status"; return 0 ;; esac
    sleep 0.5
  done
  echo "timeout"
}

expect() { # descrição esperado obtido
  [[ "$2" == "$3" ]] || fail "$1: esperado '$2', obtido '$3'"
  echo "ok  $1"
}

echo "==> subindo ambiente (porta $PORT)"
E2E_API_PORT="$PORT" "${SRC[@]}" up -d --build --wait >/dev/null 2>&1 || inconclusive "compose up falhou"
B="http://127.0.0.1:$PORT"
wait_ready "$B" 120 || { "${SRC[@]}" logs --no-color api | tail -50; inconclusive "API não ficou pronta"; }

echo "==> identidade"
without_code=$(curl -sf -X POST "$B/v1/auth/magic-link" -H 'Content-Type: application/json' \
  -d '{"email":"e2e-a@example.com"}' | jq -r '.magicLink // "ausente"')
expect "link não aparece sem código de demo" "ausente" "$without_code"
forged=$(curl -sf -X POST "$B/v1/auth/magic-link" -H 'Content-Type: application/json' -H 'X-Forwarded-For: 127.0.0.1' \
  -d '{"email":"e2e-a@example.com"}' | jq -r '.magicLink // "ausente"')
expect "X-Forwarded-For forjado não revela link" "ausente" "$forged"

login() { # email -> "session apikey"
  local link session key
  link=$(curl -sf -X POST "$B/v1/auth/magic-link" -H 'Content-Type: application/json' \
    -d "{\"email\":\"$1\",\"accessCode\":\"$CODE\"}" | jq -r .magicLink)
  session=$(curl -sf -X POST "$B/v1/auth/verify" -H 'Content-Type: application/json' \
    -d "{\"token\":\"${link##*token=}\"}" | jq -r .sessionToken)
  key=$(curl -sf -X POST "$B/v1/account/api-keys" -H "Authorization: Bearer $session" \
    -H 'Content-Type: application/json' -d '{"name":"e2e"}' | jq -r .apiKey)
  echo "$session $key"
}
read -r SESSION_A KEY_A <<<"$(login e2e-a@example.com)"
read -r SESSION_B KEY_B <<<"$(login e2e-b@example.com)"
[[ "$KEY_A" == fn_live_* && "$KEY_B" == fn_live_* ]] || fail "login/API key"
echo "ok  login com código, sessão e API key"

echo "==> jobs"
upload() { curl -sf -X POST "$B/v1/jobs" -H "Authorization: Bearer $KEY_A" -F "file=@$1" -F "format=${2:-auto}" | jq -r .jobId; }
OFX=$(upload "$FIX/sample.ofx")
CSV=$(upload "$FIX/extrato-com-saldo.csv")
CNAB=$(upload "$FIX/cnab240-retorno-cobranca.ret")
expect "OFX processado" "succeeded" "$(wait_job "$B" "$KEY_A" "$OFX")"
expect "CSV com saldo processado" "succeeded" "$(wait_job "$B" "$KEY_A" "$CSV")"
expect "CNAB processado" "succeeded" "$(wait_job "$B" "$KEY_A" "$CNAB")"
expect "prova de fechamento do CSV" "verified" \
  "$(curl -sf "$B/v1/jobs/$CSV" -H "Authorization: Bearer $KEY_A" | jq -r .balanceCheck.status)"
expect "eventos do CNAB" "2" \
  "$(curl -sf "$B/v1/jobs/$CNAB/events" -H "Authorization: Bearer $KEY_A" | jq '.events | length')"

echo "==> autorização negativa e limites"
expect "conta B não lê job de A" "404" "$(curl -s -o /dev/null -w '%{http_code}' "$B/v1/jobs/$OFX/events" -H "Authorization: Bearer $KEY_B")"
expect "sem credencial" "401" "$(curl -s -o /dev/null -w '%{http_code}' "$B/v1/jobs/$OFX")"
expect "sessão não vale em /v1/jobs" "401" "$(curl -s -o /dev/null -w '%{http_code}' "$B/v1/jobs/$OFX" -H "Authorization: Bearer $SESSION_A")"
expect "health responde" "200" "$(curl -s -o /dev/null -w '%{http_code}' "$B/actuator/health")"
head -c $((11 * 1024 * 1024)) /dev/zero > "$WORK/grande.csv"
expect "upload acima de 10 MB recusado" "413" \
  "$(curl -s -o /dev/null -w '%{http_code}' -X POST "$B/v1/jobs" -H "Authorization: Bearer $KEY_A" -F "file=@$WORK/grande.csv")"
curl -sf -X POST "$B/v1/auth/logout" -H "Authorization: Bearer $SESSION_B" >/dev/null
expect "logout invalida a sessão" "401" "$(curl -s -o /dev/null -w '%{http_code}' "$B/v1/auth/me" -H "Authorization: Bearer $SESSION_B")"

echo "==> backup -> restore em banco vazio"
for j in "$OFX" "$CSV" "$CNAB"; do curl -sf "$B/v1/jobs/$j/events" -H "Authorization: Bearer $KEY_A" | jq -S .events; done > "$WORK/source.json"
COMPOSE_FILE="$FILE" COMPOSE_PROJECT_NAME=filenorm-e2e "$ROOT/scripts/backup.sh" "$WORK/backup" >/dev/null || fail "backup.sh"
E2E_API_PORT="$RESTORE_PORT" "${DST[@]}" up -d --wait postgres >/dev/null 2>&1 || inconclusive "postgres de restore não subiu"
E2E_API_PORT="$RESTORE_PORT" COMPOSE_FILE="$FILE" COMPOSE_PROJECT_NAME=filenorm-e2e-restore \
  "$ROOT/scripts/restore.sh" "$WORK/backup" >/dev/null || fail "restore.sh"
E2E_API_PORT="$RESTORE_PORT" "${DST[@]}" up -d api >/dev/null 2>&1 || inconclusive "API restaurada não subiu"
R="http://127.0.0.1:$RESTORE_PORT"
wait_ready "$R" 120 || inconclusive "API restaurada não ficou pronta"
for j in "$OFX" "$CSV" "$CNAB"; do curl -sf "$R/v1/jobs/$j/events" -H "Authorization: Bearer $KEY_A" | jq -S .events; done > "$WORK/restored.json"
cmp -s "$WORK/source.json" "$WORK/restored.json" || fail "eventos restaurados diferem da origem"
echo "ok  eventos idênticos após restore ($(jq -s 'map(length) | add' "$WORK/restored.json") eventos)"
E2E_API_PORT="$RESTORE_PORT" COMPOSE_FILE="$FILE" COMPOSE_PROJECT_NAME=filenorm-e2e-restore \
  "$ROOT/scripts/restore.sh" "$WORK/backup" >/dev/null 2>&1 && fail "restore sobre banco populado deveria ser recusado"
echo "ok  restore sobre banco populado recusado"

echo "SUCESSO"

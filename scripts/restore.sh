#!/usr/bin/env bash
# Restaura um backup de scripts/backup.sh num ambiente VAZIO (banco recém-criado, volume de uploads vazio).
# Recusa restaurar sobre um banco que já tem tabelas, para não sobrescrever produção por engano.
# Suba só o banco antes (`docker compose up -d postgres`); a API sobe depois, já sobre os dados restaurados.
# Uso: COMPOSE_FILE=docker-compose.prod.yml ./scripts/restore.sh <diretório-do-backup>
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC="${1:?informe o diretório do backup}"
COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.prod.yml}"
DB_NAME="${FILENORM_DB_NAME:-filenorm}"
DB_USER="${FILENORM_DB_USER:-filenorm}"
case "$COMPOSE_FILE" in /*) ;; *) COMPOSE_FILE="$ROOT/$COMPOSE_FILE" ;; esac
COMPOSE=(docker compose -f "$COMPOSE_FILE")
[ -n "${COMPOSE_PROJECT_NAME:-}" ] && COMPOSE=(docker compose -p "$COMPOSE_PROJECT_NAME" -f "$COMPOSE_FILE")

( cd "$SRC" && sha256sum -c SHA256SUMS )
TABLES=$("${COMPOSE[@]}" exec -T postgres psql -U "$DB_USER" -d "$DB_NAME" -tAc \
  "select count(*) from information_schema.tables where table_schema = 'public'")
if [ "$TABLES" != "0" ]; then
  echo "banco $DB_NAME não está vazio ($TABLES tabelas); restauração abortada" >&2
  exit 1
fi
"${COMPOSE[@]}" exec -T postgres pg_restore -U "$DB_USER" -d "$DB_NAME" --no-owner --exit-on-error < "$SRC/filenorm.dump"
"${COMPOSE[@]}" run --rm --no-deps -T --entrypoint tar api -C /data -xzf - < "$SRC/uploads.tar.gz"
echo "restaurado de $SRC; suba a API (docker compose up -d api) e confira /v1/jobs/{id}/events"

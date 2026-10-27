#!/usr/bin/env bash
# Backup consistente: primeiro o banco (pg_dump formato custom), depois os arquivos originais.
# Como o arquivo é gravado antes do job ser confirmado, todo job do dump tem seu arquivo no tar.
# Uso: COMPOSE_FILE=docker-compose.prod.yml ./scripts/backup.sh [diretório-destino]
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.prod.yml}"
DB_NAME="${FILENORM_DB_NAME:-filenorm}"
DB_USER="${FILENORM_DB_USER:-filenorm}"
DEST="${1:-$ROOT/backups/$(date -u +%Y%m%dT%H%M%SZ)}"
case "$COMPOSE_FILE" in /*) ;; *) COMPOSE_FILE="$ROOT/$COMPOSE_FILE" ;; esac
COMPOSE=(docker compose -f "$COMPOSE_FILE")
[ -n "${COMPOSE_PROJECT_NAME:-}" ] && COMPOSE=(docker compose -p "$COMPOSE_PROJECT_NAME" -f "$COMPOSE_FILE")

mkdir -p "$DEST"
"${COMPOSE[@]}" exec -T postgres pg_dump -U "$DB_USER" -d "$DB_NAME" -Fc > "$DEST/filenorm.dump"
"${COMPOSE[@]}" exec -T api tar -C /data -czf - uploads > "$DEST/uploads.tar.gz"
( cd "$DEST" && sha256sum filenorm.dump uploads.tar.gz > SHA256SUMS )
echo "backup em $DEST"

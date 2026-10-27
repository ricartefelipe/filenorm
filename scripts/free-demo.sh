#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

# Código exigido para o link de login aparecer na tela: sem ele, quem tiver a URL pública
# não consegue entrar como outra pessoa. Reaproveita o código salvo entre execuções.
CODE_FILE="$ROOT/.demo-access-code"
if [[ -z "${FILENORM_DEMO_ACCESS_CODE:-}" ]]; then
  if [[ ! -s "$CODE_FILE" ]]; then
    (umask 077 && head -c 18 /dev/urandom | base64 | tr -dc 'A-Za-z0-9' | head -c 16 > "$CODE_FILE")
  fi
  FILENORM_DEMO_ACCESS_CODE="$(cat "$CODE_FILE")"
fi
export FILENORM_DEMO_ACCESS_CODE

echo "==> Empacotando API (Maven local)"
(cd apps/api && mvn -q -DskipTests package)

echo "==> Subindo demo gratuita (Docker + túnel Cloudflare)"
docker compose -f docker-compose.free.yml up -d --build

echo "==> Aguardando URL pública do túnel..."
PUBLIC_URL=""
for _ in $(seq 1 60); do
  PUBLIC_URL="$(docker compose -f docker-compose.free.yml logs tunnel 2>/dev/null \
    | grep -oE 'https://[a-zA-Z0-9-]+\.trycloudflare\.com' \
    | tail -n 1 || true)"
  if [[ -n "${PUBLIC_URL}" ]]; then
    break
  fi
  sleep 2
done

echo
echo "Local (neste PC):"
echo "  Painel/API: http://localhost:9081"
echo "  Mailpit:    http://localhost:18026"
echo
if [[ -n "${PUBLIC_URL}" ]]; then
  echo "Público (grátis, muda a cada restart):"
  echo "  ${PUBLIC_URL}"
  echo "  Upload: POST ${PUBLIC_URL}/v1/jobs"
else
  echo "Túnel ainda sem URL. Veja: docker compose -f docker-compose.free.yml logs -f tunnel"
fi
echo
echo "Login: informe o código de acesso da demo para o magic link aparecer na tela."
echo "  Código: ${FILENORM_DEMO_ACCESS_CODE}  (entregue só a quem deve acessar)"
echo "  Sem o código, o link vai apenas para o Mailpit local."
echo "Não envie extratos reais para a demo."
echo "Parar: docker compose -f docker-compose.free.yml down"

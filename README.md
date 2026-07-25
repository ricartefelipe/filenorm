# FileNorm

API de normalização de arquivos financeiros. Envie OFX, CNAB, CSV bancário ou extrato PDF e receba eventos padronizados (crédito/débito, valor, data, contraparte, chave de idempotência).

Guia de uso: [`docs/USAGE.md`](docs/USAGE.md)

## Stack

- Java 21, Spring Boot 3, PostgreSQL 16
- Jobs assíncronos no Postgres (`SKIP LOCKED`)
- Painel em Next.js (chaves, créditos, jobs)
- Cobrança pay-per-use via pacotes de créditos (Stripe)

## Estrutura

```
apps/api   — ingestão, parsers, jobs, billing
apps/web   — dashboard
deploy/    — Caddy
docs/      — especificação, plano e uso
```

## Desenvolvimento local

```bash
docker compose up -d
cp .env.example .env

cd apps/api && mvn spring-boot:run
cd apps/web && npm install && npm run dev
```

- API: `http://localhost:8080`
- Painel: `http://localhost:3000`
- Postgres: `localhost:5434`
- Mailpit: `http://localhost:18026`
- Testes: `cd apps/api && mvn test`

## Demo gratuita (sem VPS/domínio)

```bash
./scripts/free-demo.sh
```

Painel em `http://localhost:9081` + URL HTTPS pública via túnel. Detalhes: [`docs/FREE.md`](docs/FREE.md).

## Produção

```bash
docker compose -f docker-compose.prod.yml up -d --build
```

## Gitflow

- `feature/*` → PR para `develop`
- `release/*` → PR para `master`
- Branches mescladas são removidas (local e remoto)

## Pacote base

`br.com.ricarte.filenorm`

# FileNorm

API de normalização de arquivos financeiros. Envie OFX, CNAB, CSV bancário ou extrato PDF e receba eventos padronizados (crédito/débito, valor, data, contraparte, chave de idempotência).

Guia de uso: [`docs/USAGE.md`](docs/USAGE.md) (após o MVP).

## Stack

- Java 21, Spring Boot 3, PostgreSQL 16
- Painel leve em Next.js (chaves, créditos, jobs)
- Cobrança pay-per-use via pacotes de créditos (Stripe)

## Estrutura

```
apps/api   — ingestão, parsers, jobs, billing
apps/web   — dashboard
docs/      — especificação e plano
```

## Desenvolvimento local

```bash
docker compose up -d
cp .env.example .env
cd apps/api && mvn spring-boot:run
cd apps/web && npm install && npm run dev
```

## Gitflow

- `feature/*` → PR para `develop`
- `release/*` → PR para `master`
- Branches mescladas são removidas (local e remoto)

## Pacote base

`br.com.ricarte.filenorm`

# FileNorm

API de normalização de arquivos financeiros. Envie OFX, CNAB 240 (retorno de cobrança FEBRABAN), CSV bancário ou extrato PDF com texto e receba eventos padronizados (crédito/débito, valor, moeda, data, contraparte, chave de idempotência). Registros que não puderam ser convertidos são listados por linha — nada é descartado em silêncio — e, quando o arquivo traz saldos, o resultado vem com a prova de fechamento (saldo inicial + movimentos = saldo final).

- Uso da API e formatos suportados: [`docs/USAGE.md`](docs/USAGE.md)
- Instalação, upgrade, backup/restore e operação: [`docs/OPERACAO.md`](docs/OPERACAO.md)
- Oferta e limites comerciais: [`docs/COMERCIAL.md`](docs/COMERCIAL.md)
- Estratégia, roadmap e roteiro de descoberta: [`docs/ESTRATEGIA.md`](docs/ESTRATEGIA.md)
- Matriz de fechamento e pendências: [`docs/FECHAMENTO.md`](docs/FECHAMENTO.md)

## Stack

- Java 21, Spring Boot 3.5, PostgreSQL 16
- Jobs assíncronos no Postgres (`SKIP LOCKED`, lease com expiração e limite de tentativas)
- Painel em Next.js (chaves, créditos, jobs)
- Cobrança pay-per-use via pacotes de créditos (Stripe; créditos só pelo webhook assinado)

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

## Testes

```bash
./scripts/test-api.sh          # API: sobe Postgres descartável (5433, tmpfs), roda mvn test e derruba
cd apps/web && npm ci && npx tsc --noEmit && npm run build
```

Os testes de integração truncam as tabelas: eles recusam rodar contra a base de desenvolvimento (5434) ou hosts remotos. CI em `.github/workflows/ci.yml`.

## Demo gratuita (sem VPS/domínio)

```bash
./scripts/free-demo.sh
```

Painel em `http://localhost:9081` + URL HTTPS pública via túnel. Detalhes: [`docs/FREE.md`](docs/FREE.md).

## Produção

```bash
docker compose -f docker-compose.prod.yml up -d --build
```

Antes de atualizar uma instalação existente, leia a seção de upgrade em [`docs/OPERACAO.md`](docs/OPERACAO.md).

## Gitflow

- `feature/*` → PR para `develop`
- `release/*` → PR para `master`
- Branches mescladas são removidas (local e remoto)

## Pacote base

`br.com.ricarte.filenorm`

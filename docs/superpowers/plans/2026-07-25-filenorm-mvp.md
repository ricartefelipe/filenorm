# FileNorm MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship a pay-per-use financial file normalization API: async jobs, OFX/CSV/CNAB240/PDF-text parsers, API keys, credit ledger, thin dashboard, Stripe packs, free demo tunnel.

**Architecture:** Spring Boot monolith with in-process worker claiming jobs via Postgres `SKIP LOCKED`; local blob store; Next.js dashboard for account/keys/billing; Cloudflare tunnel compose for free demo.

**Tech Stack:** Java 21, Spring Boot 3.3.1, Liquibase, PostgreSQL 16, Next.js 15, Stripe Java SDK, Docker, Caddy, cloudflared.

## Global Constraints

- Package base: `br.com.ricarte.filenorm`
- Gitflow: `feature/*` → `develop`; `release/*` → `master`; delete merged branches
- No AI attribution in any artifact; avoid noisy code comments
- Responses to user in Portuguese; commits/PR bodies in English is fine (match HookGuard)
- Upload max 10 MB; file bytes retained 24h; free grant 500 credits
- Async-only jobs in MVP

## File map

```
apps/api/
  pom.xml, Dockerfile, Dockerfile.prebuilt
  src/main/java/br/com/ricarte/filenorm/
    FilenormApplication.java
    config/          FilenormProperties, WebConfig, BlobConfig
    domain/          Account, ApiKey, CreditLedger, Job, NormalizedEvent entities + repos
    auth/            Magic link + session + ApiKeyAuthFilter
    billing/         CreditsService, StripeCheckout, webhook
    jobs/            JobController, JobService, JobWorker
    parse/           FormatDetector, OfxParser, CsvParser, Cnab240Parser, PdfTextParser, ParseResult
    storage/         BlobStore, LocalBlobStore
    web/             ApiException, handlers, AccountContext
  src/main/resources/application.yml, db/changelog/*
  src/test/java/...  parser + job integration tests
apps/web/            Next.js: login, keys, jobs list, billing
deploy/              Caddyfile, Caddyfile.free
docker-compose.yml, docker-compose.prod.yml, docker-compose.free.yml
scripts/free-demo.sh
docs/USAGE.md, docs/FREE.md
.env.example
```

---

### Task 1: Foundation (compose, schema, account, API keys, credits)

**Files:** create compose, Liquibase changelog, domain entities, credit ledger, magic-link auth, API key CRUD.

- [ ] Docker compose Postgres (+ Mailpit)
- [ ] Tables: `accounts`, `login_tokens`, `sessions`, `api_keys`, `credit_ledger`, `jobs`, `normalized_events`
- [ ] Bootstrap app + properties
- [ ] Magic link auth (expose link in demo)
- [ ] API key create/list/revoke; hash at rest
- [ ] Credit grant on signup (500); `CreditsService.reserve/commit/refund`
- [ ] Tests: signup → create key → balance 500
- [ ] Commit on `feature/mvp-foundation`

### Task 2: Job pipeline + OFX parser

**Files:** `jobs/*`, `parse/OfxParser`, `storage/LocalBlobStore`, worker.

- [ ] `POST /v1/jobs` (multipart) with API key → 202
- [ ] Worker claims `queued` jobs, runs parser, writes events, debits credits on success
- [ ] OFX parser unit tests with fixture
- [ ] Integration: upload sample OFX → succeeded → events + credit debit
- [ ] Commit

### Task 3: CSV presets + CNAB240 + PDF-text

**Files:** `CsvParser`, `Cnab240Parser`, `PdfTextParser`, fixtures.

- [ ] CSV presets `generic|nubank|inter`
- [ ] CNAB240 return essential fields
- [ ] PDF text extraction (PDFBox); fail `pdf_text_unavailable` if empty
- [ ] Format `auto` detection by magic/content
- [ ] Unit tests per parser
- [ ] Commit

### Task 4: Dashboard + Stripe credit packs

**Files:** `apps/web/*`, billing controllers.

- [ ] Next.js: login, API keys, recent jobs, billing checkout
- [ ] Stripe Checkout packs + webhook top-up
- [ ] `NEXT_PUBLIC_API_BASE_URL` / same-origin support
- [ ] Commit

### Task 5: Free demo + docs + release

**Files:** `docker-compose.free.yml`, `scripts/free-demo.sh`, USAGE, FREE, prod compose.

- [ ] Free stack with tunnel
- [ ] USAGE.md + FREE.md + README polish
- [ ] PR → develop → release → master → tag `v0.1.0`
- [ ] Delete orphan branches

## Verification

```bash
docker compose up -d
cd apps/api && mvn test
cd apps/web && npm run build
./scripts/free-demo.sh
curl -F file=@sample.ofx -H "Authorization: Bearer $KEY" http://localhost:9080/v1/jobs
```

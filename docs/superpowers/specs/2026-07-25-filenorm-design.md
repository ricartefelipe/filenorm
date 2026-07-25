# FileNorm — Design Spec

**Date:** 2026-07-25  
**Status:** Ready for implementation planning  
**Mission:** Pay-per-use API that turns messy Brazilian bank files into normalized ledger events, with self-serve credits and the smallest ops surface that still earns trust.

## 1. Product

### Promise

Envie o arquivo do banco. Receba eventos financeiros normalizados — sem parser por instituição no seu código.

### Problem

Fintechs, ERPs e times de conciliação gastam semanas em parsers frágeis para OFX, CNAB, CSV e PDF de extrato. Cada banco muda layout; o custo de manutenção nunca some.

### ICP

Times pequenos/médios no Brasil que já conciliam ou importam extratos (SaaS financeiro, backoffice, contabilidade embutida) e querem API key + créditos, sem projeto de integração longo.

### Success metric (MVP)

Um usuário cria API key, envia um OFX/CSV de teste, recebe JSON normalizado e vê créditos debitados — em menos de 15 minutos, sem suporte humano.

## 2. Scope

### In MVP

1. Auth por API key (`Authorization: Bearer fn_...`)
2. Upload `POST /v1/jobs` (multipart) ou body binário com `Content-Type` + `X-FileNorm-Format`
3. Processamento assíncrono: job `queued → running → succeeded|failed`
4. Poll `GET /v1/jobs/{id}` + resultado `GET /v1/jobs/{id}/events`
5. Parsers:
   - OFX 1.x / 2.x (crédito/débito, data, valor, memo, fitid)
   - CSV bancário com presets: `generic`, `nubank`, `inter` (mapeamento de colunas)
   - CNAB 240 retorno (segmento U/T essencial: valor, data, nosso número quando presente)
   - PDF digital (texto embutido): heurística de linhas valor/data — sem OCR de imagem no MVP
6. Evento normalizado (contrato estável):
   - `externalId` (idempotency)
   - `postedAt` (ISO-8601 date)
   - `amount` (decimal string, sinal: crédito positivo / débito negativo)
   - `currency` (default BRL)
   - `direction` (`credit` | `debit`)
   - `counterparty` (string nullable)
   - `description`
   - `rawRef` (trecho/id do arquivo de origem)
   - `confidence` (0–1)
7. Conta + painel: signup magic link (exposto em demo), criar/revogar API keys, ver uso e jobs
8. Créditos: free grant inicial; pacotes Stripe Checkout; débito por job bem-sucedido (por página PDF ou por arquivo para OFX/CSV/CNAB)
9. Retenção de arquivos: 24h (binário); eventos/metadados 30 dias no free
10. Demo gratuita: Docker local + túnel Cloudflare (mesmo padrão HookGuard)

### Out of MVP

- OCR de PDF escaneado / imagem
- CNAB 400 completo e remessa
- Webhook de conclusão de job
- Transformações custom (regras por cliente)
- Multi-moeda avançada
- Conciliação automática contra ledger externo
- SDKs oficiais além de exemplos curl

## 3. Architecture

```
Client ──API key──► API (Spring Boot)
                      ├─ jobs + credit ledger (Postgres)
                      ├─ workers (in-process, SKIP LOCKED)
                      ├─ parsers (OFX / CSV / CNAB240 / PDF-text)
                      └─ object bytes (local disk MVP; S3-ready path)
Dashboard (Next.js) ──session──► API (/v1/account/*)
Stripe Checkout ──webhook──► credit top-up
```

- Modular monolith; worker no mesmo processo (perfil habilitável).
- Fila de jobs em Postgres (`FOR UPDATE SKIP LOCKED`).
- Arquivos em `./data/uploads` no MVP; interface `BlobStore` para trocar depois.
- Pacote: `br.com.ricarte.filenorm`.

## 4. API surface (MVP)

| Method | Path | Auth | Notes |
|--------|------|------|-------|
| POST | `/v1/jobs` | API key | cria job; retorna `202` + `jobId` |
| GET | `/v1/jobs/{id}` | API key | status + métricas |
| GET | `/v1/jobs/{id}/events` | API key | página de eventos normalizados |
| GET | `/v1/account/usage` | session | créditos e consumo |
| POST | `/v1/billing/checkout` | session | pacote de créditos |
| POST | `/v1/billing/stripe/webhook` | Stripe sig | top-up |
| CRUD | `/v1/account/api-keys` | session | keys |

Job create accepts `format` hint: `auto|ofx|csv|cnab240|pdf`.  
CSV accepts `preset=generic|nubank|inter`.

## 5. Credits & pricing (initial)

| Action | Cost |
|--------|------|
| Conta nova | +500 créditos free |
| OFX / CSV / CNAB (arquivo) | 1 crédito / arquivo (até 5 MB) |
| PDF | 2 créditos / página detectada (mín. 2) |
| Job failed (parse error) | 0 créditos |

Pacotes Stripe (nomes): Starter 5.000 · Growth 50.000 · Scale 250.000.  
Sem créditos → `402 insufficient_credits`.

## 6. Security & compliance

- API keys hashed at rest (SHA-256); prefixo visível `fn_live_xxxx`.
- Upload size limit 10 MB MVP.
- SSRF N/A (sem fetch de URL no MVP — só upload).
- Arquivos apagados após 24h; não logar body completo em produção.
- LGPD: painel com “apagar meus dados”; DPA fora do MVP (doc curta em USAGE).
- Stripe e secrets só via env.

## 7. Tech choices

| Layer | Choice |
|-------|--------|
| API | Java 21, Spring Boot 3.3.x |
| DB | PostgreSQL 16 + Liquibase |
| Web | Next.js 15 |
| Billing | Stripe Checkout + webhook |
| Auth painel | Magic link (+ expose em demo) |
| Deploy demo | docker-compose.free + cloudflared |
| Deploy prod | docker-compose.prod + Caddy |

## 8. Approaches considered

1. **Só sync** — simples, mas estoura timeout em PDF/CNAB grandes.  
2. **Só async (recomendado)** — um modelo mental, escala, encaixa créditos pós-sucesso.  
3. **Hybrid sync+async** — DX melhor em arquivo pequeno, duas superfícies.  

**Decisão:** async-only no MVP. Sync pode voltar depois para OFX &lt; 200KB.

## 9. Risks

| Risk | Mitigation |
|------|------------|
| Layouts CSV infinitos | Presets + `generic` documentado; confidence baixa quando colunas faltam |
| PDF sem texto | Falha clara `pdf_text_unavailable` (OCR = pós-MVP) |
| Abuso de free credits | Rate limit + tamanho + 1 conta/e-mail |
| Concorrência (Plaid/etc.) | Foco BR (CNAB/OFX/CSV bancos locais), preço por crédito transparente |

## 10. Delivery plan (high level)

1. Foundation: schema, account, API keys, credits ledger  
2. Job pipeline + OFX parser + tests  
3. CSV presets + CNAB240 + PDF-text  
4. Dashboard + Stripe packs  
5. Free demo compose + docs + release  

## 11. Non-goals for v1

Marketplace features, WarRoom, generic document AI, multi-tenant white-label.

# FileNorm — Como usar

## 1. Entrar e criar API key

1. Abra o painel e peça um magic link.
2. Em demo, o link aparece na tela.
3. Em Conta → crie uma API key (`fn_live_...`). Copie na hora.

## 2. Enviar um arquivo

```bash
curl -X POST "https://SEU_DOMINIO/v1/jobs" \
  -H "Authorization: Bearer SUA_API_KEY" \
  -F "file=@extrato.ofx" \
  -F "format=auto"
```

Resposta `202`:

```json
{"jobId":"...","status":"queued"}
```

## 3. Consultar resultado

```bash
curl -H "Authorization: Bearer SUA_API_KEY" \
  "https://SEU_DOMINIO/v1/jobs/{jobId}"

curl -H "Authorization: Bearer SUA_API_KEY" \
  "https://SEU_DOMINIO/v1/jobs/{jobId}/events"
```

Formatos: `auto`, `ofx`, `csv` (+ `preset=generic|nubank|inter`), `cnab240`, `pdf`.

## 4. Créditos

| Ação | Custo |
|------|-------|
| Conta nova | +500 free |
| OFX / CSV / CNAB | 1 / arquivo |
| PDF | 2 / página (mín. 2) |

Sem créditos → HTTP 402. Painel → Billing → pacotes Stripe.

## 5. Demo gratuita

Veja [`FREE.md`](FREE.md).

## 6. Produção

```bash
cp .env.example .env
docker compose -f docker-compose.prod.yml up -d --build
```

# FileNorm — Como usar

## 1. Entrar e criar API key

1. Abra o painel e peça um magic link.
2. Em demo, o link aparece na tela.
3. Em Conta → crie uma API key (`fn_live_...`). Copie na hora.

A API key deixa de funcionar quando é revogada ou quando a conta é desativada/expira.

## 2. Enviar um arquivo

```bash
curl -X POST "https://SEU_DOMINIO/v1/jobs" \
  -H "Authorization: Bearer SUA_API_KEY" \
  -F "file=@extrato.ofx" \
  -F "format=auto"
```

Resposta `202`:

```json
{"jobId":"...","status":"queued","format":"ofx","estimatedCredits":1}
```

Parâmetros: `format` = `auto` | `ofx` | `csv` | `cnab240` | `pdf`; `preset` (CSV) = `generic` | `nubank` | `inter`.
Valores desconhecidos retornam `400` (`unsupported_format`, `unknown_preset`); `format=pdf` com conteúdo que não é PDF retorna `file_not_pdf`.

## 3. Consultar resultado

```bash
curl -H "Authorization: Bearer SUA_API_KEY" "https://SEU_DOMINIO/v1/jobs/{jobId}"
curl -H "Authorization: Bearer SUA_API_KEY" "https://SEU_DOMINIO/v1/jobs/{jobId}/events"
curl -H "Authorization: Bearer SUA_API_KEY" "https://SEU_DOMINIO/v1/jobs/{jobId}/issues"
curl -X DELETE -H "Authorization: Bearer SUA_API_KEY" "https://SEU_DOMINIO/v1/jobs/{jobId}"
```

### Status do job

| Status | Significado | Créditos |
|---|---|---|
| `queued` | aguardando worker | — |
| `running` | em processamento (lease de 5 min; se o worker morrer, outro reassume) | — |
| `succeeded` | todos os registros financeiros viraram eventos | cobrado |
| `partial` | eventos emitidos, mas há registros **rejeitados** (veja `/issues`) | cobrado |
| `failed` | arquivo não processado (`errorCode`); nenhum evento publicado | não cobrado |

Um job só fica `succeeded`/`partial` quando eventos, issues e cobrança foram gravados juntos numa transação.
Após `max-attempts` (3) tentativas interrompidas, o job falha com `max_attempts_exceeded`.

### Prova de fechamento (`balanceCheck`)

Quando o arquivo traz saldos, o FileNorm confere **saldo inicial + créditos − débitos = saldo final**, ao centavo:

```json
"balanceCheck": {
  "status": "verified",
  "source": "csv_running_balance",
  "openingBalance": "1000.00",
  "closingBalance": "2000.00",
  "computedClosingBalance": "2000.00",
  "difference": "0.00"
}
```

| `status` | Significado |
|---|---|
| `verified` | fechou: nenhum movimento faltando, sobrando ou com sinal trocado |
| `mismatch` | não fechou; `difference` = saldo declarado − calculado. O job fica `partial` com a issue `balance_mismatch` |
| `unavailable` | o arquivo não traz os dois saldos (`reason`) |

Por formato:

- **CSV com coluna de saldo** (`Saldo`/`Balance`): conferência **linha a linha**. A linha onde a sequência quebra recebe a issue `csv_balance_break` ("saldo esperado X, informado Y"). Aceita extratos em ordem crescente ou decrescente e linhas só de saldo ("SALDO ANTERIOR"). A conferência começa na primeira linha que tem saldo.
- **PDF:** saldo inicial na linha "saldo anterior/inicial"; saldo final na última linha de saldo (ignora saldo bloqueado/disponível/limite/aplicações). Pega, por exemplo, débitos impressos sem sinal.
- **OFX:** o padrão só declara o saldo final (`LEDGERBAL`), que é registrado; a prova fica `unavailable` (`opening_balance_missing`). A conferência entre extratos consecutivos da mesma conta está no backlog.
- **CNAB 240 retorno:** não tem saldo (`no_balances_in_format`); a integridade vem da validação de header, trailer e quantidade de registros.

### Evento normalizado

```json
{
  "externalId": "20250710001",
  "idempotencyKey": "5d81b6ff…(64 hex)",
  "postedAt": "2025-07-10",
  "amount": "42.50",
  "currency": "BRL",
  "direction": "debit",
  "counterparty": "Padaria Central",
  "description": "Compra debito",
  "rawRef": "<trecho original>",
  "confidence": 0.950,
  "ordinal": 0
}
```

- `amount` é sempre positivo com 2 casas; o sinal está em `direction`.
- `idempotencyKey` é estável entre reenvios do mesmo arquivo: use-a para deduplicar lançamentos no seu sistema.
- `confidence` é a confiança do método de extração (não uma probabilidade calibrada): OFX 0,95 (0,85 sem FITID, 0,70 se TRNTYPE contradiz o sinal), CNAB 0,90 (0,80 sem data de crédito), CSV com preset 0,90 / genérico 0,80, PDF 0,60.
- `rawRef` guarda o trecho original da linha/bloco, visível só para a conta dona do job.

### Issues (`/issues`)

Cada registro que não virou evento aparece com `severity`, `code`, `record` (ex.: `line 4`) e um detalhe curto:

- `rejected` — registro com aparência de transação mas inválido (data impossível, valor ambíguo, segmento sem par). Torna o job `partial`.
- `skipped` — registro sem efeito financeiro (linha de saldo, ocorrência CNAB sem pagamento, valor zero).
- `warning` — evento emitido com ressalva (FITID ausente ou repetido, TRNTYPE contraditório).

Até 1.000 issues são armazenadas por job; os totais ficam em `rejectedCount`, `skippedCount` e `warningCount`.

## 4. Formatos suportados

| Formato | Suportado | Fora do escopo |
|---|---|---|
| OFX | 1.x (SGML) e 2.x (XML); `CHARSET`/`encoding` declarado; moeda por `CURDEF`; valores `-42.50` ou `1.234,56` | múltiplas contas com moedas diferentes no mesmo arquivo |
| CSV | delimitador `;` `,` tab `\|`; aspas RFC 4180; UTF-8 ou Windows-1252; datas `aaaa-mm-dd`, `dd/mm/aaaa`, `dd-mm-aaaa`, `aaaa/mm/dd`; valores pt-BR ou en-US; moeda BRL | colunas separadas de débito/crédito; valores ambíguos como `1.500` sem centavos (rejeitados) |
| CNAB 240 | **retorno de cobrança FEBRABAN (serviço 01), segmentos T/U**, posições do padrão v08–v10; valida tamanho das linhas, header, trailer e quantidade de registros | remessa, extrato (serviço 04), pagamentos (segmentos A/J), leiautes de banco que divergem do padrão FEBRABAN |
| PDF | extratos com camada de texto; linha com data `dd/mm/aaaa` e valor pt-BR (`-`, `D`/`C`); até 100 páginas | PDF escaneado (sem OCR), datas sem ano (rejeitadas), layouts de várias colunas |

Os presets `nubank` e `inter` localizam colunas pelos cabeçalhos (`Data`, `Valor`, `Descrição` / `Data Lançamento`, `Histórico`). Foram validados com arquivos sintéticos, não com exportações reais desses bancos.

Limites padrão: 10 MB por upload, 50.000 registros por arquivo e 100 páginas de PDF.

## 5. Créditos

| Ação | Custo |
|------|-------|
| Conta nova | +500 free |
| OFX / CSV / CNAB | 1 / arquivo |
| PDF | 2 / página (mín. 2) |

- Sem créditos no envio → HTTP 402. Se o saldo acabar entre o envio e o processamento, o job falha com `insufficient_credits` e não é cobrado.
- Jobs `failed` não são cobrados; `partial` é cobrado (há resultado entregue).
- Painel → Billing → pacotes Stripe (Starter 5.000, Growth 50.000, Scale 250.000). Os créditos entram só quando o Stripe confirma o pagamento pelo webhook assinado; voltar do checkout não credita nada.

## 6. Exclusão e retenção

- `DELETE /v1/jobs/{id}` remove eventos, issues e o arquivo original (o lançamento de créditos fica no histórico para auditoria). Jobs em `running` retornam `409`.
- `FILENORM_RETENTION_DAYS` (padrão `0` = desligado) remove automaticamente jobs finalizados mais antigos que N dias.

## 7. Demo gratuita

Veja [`FREE.md`](FREE.md).

## 8. Produção

```bash
cp .env.example .env
docker compose -f docker-compose.prod.yml up -d --build
```

Operação, upgrade e backup: [`OPERACAO.md`](OPERACAO.md).

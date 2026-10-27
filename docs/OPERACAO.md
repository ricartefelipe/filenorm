# FileNorm — Operação

## Instalação (Docker Compose)

```bash
cp .env.example .env    # defina FILENORM_DOMAIN, FILENORM_DB_PASSWORD, SMTP e (opcional) Stripe
docker compose -f docker-compose.prod.yml up -d --build
```

Serviços: `postgres`, `uploads-permissions` (ajuste único de dono do volume, termina em seguida), `api` (uid 10001, sem root), `web` (usuário `node`), `caddy` (TLS automático).

### Configuração segura (produção)

| Variável | Produção | Por quê |
|---|---|---|
| `FILENORM_EXPOSE_MAGIC_LINK` | `false` (já fixo no compose de produção) | `true` devolve o link de login na resposta — só para demo |
| `FILENORM_DB_PASSWORD` | obrigatório, forte | o compose recusa subir sem ele |
| `STRIPE_WEBHOOK_SECRET` | `whsec_...` do endpoint | sem ele o webhook responde 503 e nenhum crédito é concedido |
| `TOTALRECALL_PROVISION_TOKEN` | segredo longo ou vazio | vazio desativa `/internal/v1/totalrecall/*` |
| `FILENORM_CORS_ORIGINS` | só o domínio do painel | |
| `FILENORM_WORKER_LEASE_SECONDS` | 300 | tempo máximo de uma tentativa antes de outro worker reassumir |
| `FILENORM_WORKER_MAX_ATTEMPTS` | 3 | depois disso o job falha com `max_attempts_exceeded` |
| `FILENORM_MAX_RECORDS` / `FILENORM_MAX_PDF_PAGES` | 50000 / 100 | limitam CPU e memória por arquivo |
| `FILENORM_RETENTION_DAYS` | conforme contrato (0 = manter) | remove jobs, eventos e originais antigos |

O Caddy deve encaminhar `/v1/billing/stripe/webhook` para a API sem alterar o corpo (a assinatura é verificada sobre o corpo bruto).

## Upgrade

1. Faça backup (abaixo).
2. `git pull` da versão aprovada e `docker compose -f docker-compose.prod.yml up -d --build`.
3. As migrations (Liquibase) rodam na subida da API. A `003` é aditiva: novas colunas com default, tabela `job_issues`, índices, unicidade de `operation_key` e `CHECK (credits_balance >= 0) NOT VALID` (não falha com saldo negativo legado; vale para escritas novas).
4. Jobs que estavam em `running` sem lease (versão anterior, worker interrompido) são reprocessados automaticamente.
5. **A partir desta versão a API não roda como root.** O serviço `uploads-permissions` faz `chown -R 10001` no volume de uploads antes da API subir. Se você usa outro orquestrador, faça o mesmo; sem isso, os uploads retornam `503 storage_unavailable`.

Ensaio realizado: banco e volume criados pela versão `84e4315` (API como root, com dados e um job travado em `running`) → upgrade para esta versão → migration aplicada, job travado concluído e cobrado uma vez, novos uploads funcionando após o ajuste de permissão.

Rollback: a migration é aditiva, então a versão anterior lê o banco migrado. Os jobs `partial` aparecem para ela como um status desconhecido.

## Backup e restauração

```bash
# backup (API e banco rodando)
COMPOSE_FILE=docker-compose.prod.yml ./scripts/backup.sh /caminho/seguro/backup-AAAAMMDD

# restauração em ambiente NOVO e vazio
docker compose -f docker-compose.prod.yml up -d postgres
COMPOSE_FILE=docker-compose.prod.yml ./scripts/restore.sh /caminho/seguro/backup-AAAAMMDD
docker compose -f docker-compose.prod.yml up -d
```

- O backup grava `filenorm.dump` (pg_dump, formato custom), `uploads.tar.gz` e `SHA256SUMS`. O banco é copiado antes dos arquivos, então todo job do dump tem seu original no tar.
- `restore.sh` confere os checksums e **recusa** restaurar sobre um banco que já tem tabelas.
- Use `COMPOSE_PROJECT_NAME` para apontar para um projeto diferente (ex.: ensaio isolado).
- O backup contém extratos de clientes: guarde-o criptografado e com acesso restrito. A rotação e a cópia externa ficam a cargo do operador.

Ensaio realizado: backup de um ambiente com 7 eventos (OFX legado, CSV parcial e CNAB) → restauração num projeto vazio → API sobre os dados restaurados devolveu os eventos **idênticos** pela API, uma exclusão leu o volume restaurado e um novo job foi processado. Uma segunda restauração sobre o banco já populado foi recusada.

Objetivos (premissas, não medições de produção): RPO = intervalo entre backups agendados pelo operador; RTO ≈ subir os containers + `pg_restore` (segundos para bases pequenas; meça com o tamanho real).

## Observabilidade

- `GET /actuator/health/liveness` e `/actuator/health/readiness`.
- `GET /actuator/prometheus` — o Caddy só publica `/actuator/health*`; colete as métricas pela rede interna (`api:8080`):
  - `filenorm_jobs_current{status="queued|running"}` — profundidade da fila;
  - `filenorm_jobs_completed_total{status,format}` — taxa de `partial`/`failed` por parser;
  - `filenorm_jobs_issues_total{severity}` — registros rejeitados/ignorados.
- Logs: uma linha por job finalizado com id, status, formato, contagens e créditos. Conteúdo de arquivos e chaves não é registrado.

Alertas sugeridos: `queued` crescendo por mais de 10 min; aumento de `failed` com `max_attempts_exceeded`; taxa de `partial` acima do histórico de um formato.

## Troubleshooting

| Sintoma | Causa provável | Ação |
|---|---|---|
| Upload `503 storage_unavailable` | volume de uploads sem permissão para uid 10001 | rode o serviço `uploads-permissions` ou `chown -R 10001` no volume |
| Job em `failed` / `max_attempts_exceeded` | worker reiniciado repetidamente durante o processamento | veja os logs da API no horário; reenvie o arquivo |
| Job `failed` / `insufficient_credits` | saldo consumido por outros jobs entre o envio e o processamento | compre créditos e reenvie |
| Job `partial` | linhas inválidas no arquivo | `GET /v1/jobs/{id}/issues` mostra linha e motivo |
| Pagamento aprovado sem créditos | webhook não configurado/assinatura inválida, ou sessão sem `payment_status=paid` (boleto pendente) | confira `STRIPE_WEBHOOK_SECRET` e o log de entregas no painel do Stripe; o reenvio do evento é seguro (idempotente) |
| `mvn test` recusa o banco | os testes só rodam em Postgres local descartável | use `./scripts/test-api.sh` |

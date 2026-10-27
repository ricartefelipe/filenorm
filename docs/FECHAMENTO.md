# FileNorm — Matriz de fechamento

Base: branch `feature/melhoria-refactor`. Rodada 1 publicada em `48d186d`; rodada 2 (itens FN-23 a FN-30, aproveitados do prompt V4) local, não commitada (27/10/2026).
Legenda de estado: **corrigido** (código + teste), **reproduzido** (falha observada antes da correção), **não aplicável**, **pendente**, **bloqueado**.

## Achados P0/P1

| ID | Prio | Achado | Evidência / reprodução | Correção | Teste | Estado |
|---|---|---|---|---|---|---|
| FN-01 | P0 | Job reivindicado ficava em `running` para sempre se o worker morresse; não havia retomada | `JobRepository.claimCandidateIds` só lia `queued` | lease (`lease_expires_at`) + `attempts` como token de fencing; reclaim de lease expirado; `max_attempts_exceeded`; jobs legados sem lease recuperados | `JobIntegrationTest.expiredLeaseIsReclaimed…`, `…FailAfterMaxAttempts`, `legacyRunningJob…`; E2E kill -9 + restart | corrigido |
| FN-02 | P0 | `insufficient_credits` no processamento marcava a transação como rollback-only: job preso e lote abortado | `CreditsService.charge` lançava exceção dentro do `@Transactional` de `processJob` | parse fora de transação; finalização única (eventos + issues + cobrança + status) condicionada à tentativa; falha gravada em transação própria; worker isola erros por job | `concurrentJobsCannotOverspend…`, `JobFailureAfterEventsIntegrationTest` | corrigido |
| FN-03 | P0 | Saldo por leitura-e-escrita em memória (lost update) e cobrança sem idempotência | `CreditsService.applyDelta` | `UPDATE … WHERE credits_balance >= :amount`; `operation_key` única no ledger (`job:<id>`, `signup:<id>`, `stripe:checkout:<session>`); `CHECK (credits_balance >= 0)` | `concurrentJobsCannotOverspendTheLastCredit` (2 threads, último crédito) | corrigido |
| FN-04 | P0 | CSV com ponto decimal multiplicava valores por 100 (`-42.50` → `4250`) | sondagem: `debit 4250` | `Amounts` detecta separador decimal e recusa valores ambíguos | `AmountsTest`, `CsvParserTest.parsesGenericPresetWithDotDecimals` | reproduzido → corrigido |
| FN-05 | P0 | PDF truncava valores sem separador de milhar (`1500,00` → `500,00`), ignorava `D`/`C` e lançava linhas de saldo | sondagem | regex de valor ancorada, sufixo D/C/`-`, linhas de saldo → `skipped` | `PdfTextParserTest` | reproduzido → corrigido |
| FN-06 | P0 | CSV aceitava `31/02/2025` como `28/02/2025` | teste novo falhou antes da correção | `ResolverStyle.STRICT` | `CsvParserTest.reportsFooterAndInvalidRows…` | reproduzido → corrigido |
| FN-07 | P0 | API key continuava válida com a conta desativada/expirada (TotalRecall `disable`) | `ApiKeyAuthFilter` não consultava a conta | filtro exige conta ativa | `ApiKeyAccountStatusIntegrationTest` (+ E2E 401) | corrigido |
| FN-08 | P0 | CNAB240: um registro inválido derrubava o arquivo inteiro; um arquivo truncado passava; a detecção por conteúdo nunca funcionava | sondagem (`DateTimeParseException`, detector → `csv`) | valida tamanho 240, header/trailer, quantidade de registros, retorno/serviço 01, pares T/U; ocorrência sem pagamento → `skipped`; data inválida → `rejected`; detector usa 8 KB | `Cnab240ParserTest` (golden + truncado, contagem, linha curta, leiaute) | reproduzido → corrigido |
| FN-09 | P0 | Webhook Stripe: reenvio creditava em dobro; créditos vinham do metadata; sem checagem de `payment_status` | `StripeWebhookController` | dedupe por sessão, créditos pelo pacote no servidor, só `paid`, `async_payment_succeeded`, desserialização tolerante à versão | `StripeWebhookIntegrationTest` (duplicado, fora de ordem, unpaid, forjado) | corrigido |
| FN-10 | P1 | Linhas inválidas descartadas em silêncio ou derrubando o job | CSV/OFX/PDF | `ParseIssue` (rejected/skipped/warning), status `partial`, `GET /v1/jobs/{id}/issues` | `csvWithInvalidRowIsPartial…` | corrigido |
| FN-11 | P1 | Encoding: Latin-1/1252 virava `�`; OFX ignorava `CHARSET`; moeda fixa em BRL | sondagem | `TextDecoding`; charset declarado; `CURDEF` | `CsvParserTest.decodesLatin1`, `OfxParserTest.honoursDeclaredCharset…` | corrigido |
| FN-12 | P1 | Chave de idempotência prometida no README não existia | grep | `idempotency_key` estável (SHA-256), único por job | `uploadsOfx…StableIdempotencyKeys` | corrigido |
| FN-13 | P1 | Checkout aceitava URLs de retorno arbitrárias (open redirect) e o painel enviava o pack `pro` (inexistente) | `BillingController`, `billing/page.tsx` | URLs restritas ao painel; pack `growth` | `checkoutRedirectsMustPointBackToTheApp`; build web | corrigido |
| FN-14 | P1 | Preflight CORS respondia 401 (painel em outra origem quebrado) | E2E: `OPTIONS` → 401 | filtros deixam passar o preflight para a configuração de CORS | `corsPreflightIsAnswered…`; E2E 200/403 | reproduzido → corrigido |
| FN-15 | P1 | `mvn test` falhava sem configuração (teste em 5433, compose em 5434); risco de rodar `TRUNCATE` na base de dev | baseline | `docker-compose.test.yml` (tmpfs) + `scripts/test-api.sh`; trava contra 5434/host remoto | execução da trava | corrigido |
| FN-16 | P1 | Sem CI de build/testes | `.github` ausente | `.github/workflows/ci.yml` (API com Postgres, web tsc/build/audit) | — | criado; **pendente** de execução no GitHub |
| FN-17 | P1 | Limites: sem teto de registros/páginas; upload de PDF falso aceito; formato/preset inválido só falhava no worker | leitura | `max-records`, `max-pdf-pages`, validação no upload, magic bytes de PDF, nome de arquivo saneado | `rejectsInvalidUploadParameters`, `enforcesPageLimit` | corrigido |
| FN-18 | P1 | Sem exclusão/retenção de dados de clientes | leitura | `DELETE /v1/jobs/{id}` (eventos, issues, original); `FILENORM_RETENTION_DAYS` | `deleteRemovesEvents…`; cross-tenant 404 | corrigido (retenção sem teste automatizado de agendamento) |
| FN-19 | P1 | Containers como root; Prometheus e actuator públicos no Caddy | Dockerfiles, Caddyfile | uid 10001 / `node`; `uploads-permissions` para volumes legados; proxy publica só `/actuator/health*` | ensaio de upgrade; `caddy validate` | corrigido |
| FN-20 | P1 | Dependências com advisories (Boot 3.3.1: Tomcat, Spring, Jackson, Logback, driver PG; Next 15.5.21 crítico) | OSV/npm audit | Boot 3.5.16, PDFBox 3.0.8, patches de Tomcat/Jackson/PG/commons-lang3; `npm audit fix` + override `postcss ^8.5.23` | 69 testes + build web | corrigido, com 2 exceções (abaixo) |
| FN-22 | P1 | Nenhuma prova de completude: um movimento faltando ou com sinal trocado passava como sucesso | sondagem: débito sem sinal no PDF virava crédito sem alerta | `BalanceCheck` (saldo inicial + movimentos = final), CSV linha a linha (`csv_balance_break`), PDF por linhas de saldo, OFX registra `LEDGERBAL`; `mismatch` → `partial` + `balance_mismatch`; migration 004; coluna no painel | `BalanceCheckTest`, `CsvParserTest` (+6), `PdfTextParserTest` (+2), `OfxParserTest` (+1), `JobIntegrationTest` (+3) | corrigido |
| FN-23 | P0 | Demo gratuita num túnel público devolvia o link de login na tela: qualquer pessoa entrava como qualquer e-mail | `docker-compose.free.yml` (`EXPOSE_MAGIC_LINK=true` + `cloudflared`) | link só aparece com `FILENORM_DEMO_ACCESS_CODE`; IP/loopback não aceito (os cabeçalhos de proxy são confiados e podem ser forjados); `free-demo.sh` gera e imprime o código; campo no login | `MagicLinkIntegrationTest.linkIsOnlyRevealedWithTheDemoAccessCode`; smoke E2E (sem código e com `X-Forwarded-For` forjado) | corrigido |
| FN-24 | P0 | Magic link usado em paralelo: consumo por leitura + delete da entidade | `AuthService.verifyMagicLink` | `DELETE` condicional (só quem apaga cria a sessão, na mesma transação) | `concurrentUseOfTheSameLinkCreatesExactlyOneSession` (4 threads → um 200, três 401, uma sessão); `expiredLinkIsRejected` | corrigido |
| FN-25 | P0 | CNAB ignorava em silêncio registros de tipo desconhecido; não validava versão de leiaute, banco, trailer de lote nem moeda | leitura do parser | `cnab_unknown_record`, `cnab_layout_version_missing`, `cnab_invalid_bank_code`, `cnab_lot_count_mismatch`, `cnab_unsupported_currency` | `Cnab240ParserTest` (+4) | corrigido |
| FN-26 | P1 | "Sair" do painel não encerrava a sessão no servidor | `AppShell.onLogout` | chama `POST /v1/auth/logout` antes de limpar o navegador | smoke E2E (sessão → 401 após logout); build web | corrigido |
| FN-27 | P1 | Imagens com HIGH/CRITICAL fora do projeto: npm embutido no `node:22-alpine` (11) e `pebble` na base `eclipse-temurin` (3); imagens e actions por tag mutável | Trivy 0.69.3 | web sem npm/yarn, Next iniciado com `node`, dev deps podadas; `pebble` removido; todas as imagens por digest e actions por SHA; Trivy como gate no CI com `.trivyignore.yaml` expirando | Trivy: web 0, API 0 fora das 2 exceções; container web sobe e responde 200 como `node` | corrigido |
| FN-28 | P1 | Arquivos originais órfãos ficavam no disco para sempre | `createJob` limpa só em rollback, não em queda do processo | varredura horária de arquivos sem job há mais de 1 h | `orphanUploadFilesAreRemovedButFilesOfExistingJobsStay` | corrigido |
| FN-29 | P1 | Estorno no Stripe sem política definida | webhook só trata pagamento | política publicada: estorno não debita créditos automaticamente (ajuste manual) | `refundsDoNotChangeTheBalanceAutomatically` | corrigido (política) |
| FN-30 | P1 | CI sem jornada integrada, restore e scan de imagem | `ci.yml` | job `e2e`: Compose real + `scripts/smoke-e2e.sh` (login, jobs, autorização negativa, limite 413, logout, backup → restore) + gate Trivy | `smoke-e2e.sh` local: 18 verificações, exit 0, 54 s | corrigido localmente; **pendente** de execução no GitHub |
| FN-21 | P1 | Backup/restore inexistentes | — | `scripts/backup.sh` / `restore.sh` (checksums, recusa banco populado) | ensaio real (ver OPERACAO.md) | corrigido |

**Não aplicável:**
- XXE no OFX: não existe parser XML (regex); o teste prova que `&ext;` não é resolvido.
- Injeção de fórmula CSV: o produto não exporta planilhas.
- Path traversal no storage: `LocalBlobStore` já normalizava e o nome do arquivo agora também é saneado.

## Gates executados (local)

| Gate | Comando | Resultado |
|---|---|---|
| Baseline API (antes) | `mvn test` com Postgres descartável | 4/4; sem banco: 1 erro |
| API (rodada 2) | `./scripts/test-api.sh` — Java 21.0.12, Maven 3.9.12, Docker 29.8.2 | **93/93**, 0 falhas, 0 skips. Sem alteração Java depois desta execução |
| Smoke E2E | `./scripts/smoke-e2e.sh` (imagem e Compose reais) | 18/18 verificações, exit 0 |
| Scan de imagens | Trivy 0.69.3 (DB de 26/10/2026), HIGH/CRITICAL, com `.trivyignore.yaml` | API 0, web 0 (antes: API 5, web 11) |
| Trava de banco | `FILENORM_TEST_DB_URL=…:5434… mvn test` | recusado, como esperado |
| Web | `npm ci && npx tsc --noEmit && npm run build` (Node 24.15, npm 12.0.2) | ok |
| SCA web | `npm audit --omit=dev` / `npm audit` | 0 / 0 (antes: 5, 1 crítica) |
| SCA Java | OSV `querybatch`, 97 pacotes de runtime | 2 pacotes com advisories (antes: 18) |
| Upgrade | imagem de `84e4315` (root) com dados → imagem nova | migration 003 ok; job travado concluído; volume corrigido pelo init |
| E2E integrado | API + Postgres + Mailpit; magic link real, API key, OFX/CSV/CNAB/PDF, kill -9 + restart, cross-account, desativação, logout, CORS | todos os resultados esperados |
| Backup/restore | `backup.sh` → projeto vazio → `restore.sh` → API | 7 eventos idênticos pela API; restore duplicado recusado |
| Segredos no histórico | `git log --all -p` com padrões de chaves | nenhum |
| `git diff --check` | — | ok |

**Não executados:**
- GitHub Actions: o workflow só dispara em PR ou push para `master`/`develop`; o push de `48d186d` na feature não o executou. Abrir o PR.
- Scan para arm64: só amd64 foi escaneado; as imagens base são multi-arquitetura, mas o resultado não se transfere.
- Lint do Next: o projeto não tem ESLint configurado, e `next lint` abriria uma configuração interativa.
- Stripe real/sandbox: não há chaves.
- SMTP real: o ensaio usou Mailpit.

### Exceções de segurança aceitas (revisar até 31/01/2027)

- `spring-webmvc 6.2.19` — CVE-2026-47890 / GHSA-j9f9-w8pj-32f8 (SSE com fragments) e CVE-2026-47884 / GHSA-pc63-qcmh-9cmg (XsltView). Correção só no Spring 7 / Boot 4 (major). O projeto é só REST e não usa SSE nem `XsltView` (grep). Registradas em `.trivyignore.yaml` com expiração; depois disso o CI volta a falhar. Plano: migrar para o Boot 4 como tarefa própria.
- `log4j-api 2.24.3` — GHSA-qv9r-c865-cp47 (moderada, `MapMessage`). O backend de log é o Logback e `MapMessage` não é usado.
- `next 15.5.28`: correções futuras só no 16 (major). O override de `postcss` foge do pin do Next e deve ser removido ao migrar.

## Risco residual

- **Heartbeat:** a renovação de lease durante o parse não foi implementada. Um parse que passe de `lease-seconds` (300 s) pode ser reassumido; o fencing por tentativa impede gravação/cobrança duplicada, mas o trabalho é refeito. Os limites atuais tornam isso improvável.
- **Thread do parser:** não há timeout de CPU por arquivo além dos limites de tamanho/páginas/registros, e um PDF patológico pode ocupar uma thread do worker.
- **CNAB:** posições FEBRABAN validadas apenas com fixture sintética; bancos que divergem do padrão falham ou são rejeitados.
- **Presets `nubank`/`inter`:** validados por cabeçalho sintético, não com exportações reais.
- **`rawRef`:** guarda a linha original (pode conter nomes), isolada por conta e removida com o job; não há criptografia em repouso própria (depende do disco).
- **Stripe:** reembolso/chargeback não estorna créditos automaticamente (política documentada e testada); a conciliação é manual.
- **Demo:** quem tem o código de acesso entra com qualquer e-mail. O código só reduz a exposição a convidados; a demo não deve receber arquivos reais.

## Backlog P2 (adiado com justificativa)

| Item | Benefício | Esforço | Pré-requisito | Por que adiar |
|---|---|---|---|---|
| Prova de fechamento entre OFX consecutivos da mesma conta (saldo final anterior + movimentos = `LEDGERBAL`) | fecha a lacuna do formato mais comum | M | identificação de conta (`ACCTID`) e regra para períodos sobrepostos | precisa de dados reais para evitar falsos alarmes |
| Heartbeat de lease + timeout de parse em executor | elimina reprocessamento raro | M | métrica de duração de parse | sem evidência de parse > 300 s |
| Fixtures reais por banco (CNAB por banco, CSVs) | precisão comprovada | M por banco | arquivos do piloto, anonimizados | depende do piloto |
| Mapeamento de colunas CSV versionado / colunas débito-crédito | mais bancos sem código | M | demanda do piloto | escopo de produto |
| Paginação de `/events` | respostas grandes | P | — | limitado a 50 mil registros hoje |
| Estorno de créditos em `charge.refunded` e reconciliação Stripe | billing completo | M | Stripe sandbox | sem venda SaaS ainda |
| OCR com confiança por campo | PDFs escaneados | G | provedor/infra | fora da oferta atual |
| ESLint no web, SBOM, Trivy no CI, actions fixadas por SHA | supply chain | P | — | gates principais já existem |
| Migração para Boot 4 / Next 16 | fecha as exceções acima | M–G | janela de testes | major; não fazer às cegas |
| Upload/download com URL temporária, criptografia em repouso | privacidade | M | storage de objeto | storage local atende instalação dedicada |
| Painel: envio pelo navegador, prévia e custo antes de executar | UX | M | — | o fluxo atual é via API |

## P3 (rejeitado no escopo atual)

- Prometer "qualquer banco/qualquer PDF": falso sem fixtures.
- Modelo de ML próprio para substituir parsers: os parsers determinísticos são auditáveis, e não há dados de treino com base legal.
- Contabilidade ou banco digital dentro do normalizador: outro produto.
- Kubernetes, microsserviços, filas externas, multi-região: uma VM com fila no Postgres atende; não há medição de carga que justifique.

## Decisões do proprietário

1. **Git:** revisar e fazer staging/commit/push dos arquivos listados abaixo; abrir PR `feature/melhoria-refactor` → `develop`; confirmar o CI verde no GitHub.
2. **Tag `v0.1.0`:** manter, e criar uma nova tag após o merge (recomendação já registrada).
3. **Credenciais:** Stripe (modo teste → produção), SMTP, `TOTALRECALL_PROVISION_TOKEN`, domínio.
4. **Oferta:** escolher entre instalação dedicada (GO piloto) e SaaS (NO-GO até os itens de [COMERCIAL.md](COMERCIAL.md)); definir preço e termos de uso/privacidade.
5. **Piloto:** cliente, fontes de arquivo e critério de aceite; autorizar o uso de arquivos reais só em ambiente do cliente.
6. **Dependências:** aceitar ou não as exceções de segurança acima e o prazo de revisão.
7. **Deploy:** agendar backup e testar o restore no ambiente real; rodar o upgrade com o serviço `uploads-permissions`.

## Arquivos para revisão/staging

- **Modificados (55):** ver `git status`.
- **Novos (rodada 2):** `.trivyignore.yaml`, `docker-compose.e2e.yml`, `scripts/smoke-e2e.sh`, `MagicLinkIntegrationTest`.
- **Novos (rodada 1, 36):**
  - `.github/workflows/ci.yml`, `docker-compose.test.yml`
  - `scripts/test-api.sh`, `scripts/backup.sh`, `scripts/restore.sh`
  - `docs/OPERACAO.md`, `docs/COMERCIAL.md`, `docs/FECHAMENTO.md`, `docs/PILOTO.md`, `docs/SPEC-PAINEL-UPLOAD.md`, `docs/ESTRATEGIA.md`
  - migrations `003-job-lease-idempotency.yaml`, `004-balance-check.yaml`
  - classes: `JobIssue`, `JobIssueRepository`, `EventKeys`, `JobClaim`, `JobMetrics`, `JobRetention`, `Amounts`, `ParseIssue`, `TextDecoding`, `BalanceCheck`, `StatementBalances`
  - testes: `BalanceCheckTest`, `AmountsTest`, `Cnab240ParserTest`, `FormatDetectorTest`, `PdfTextParserTest`, `ApiKeyAccountStatusIntegrationTest`, `StripeWebhookIntegrationTest`, `JobFailureAfterEventsIntegrationTest`, `TestAccounts`
  - fixtures: `cnab240-retorno-cobranca.ret`, `nubank-conta.csv`, `extrato-com-saldo.csv`
- **Removidos:** nenhum.
- **Ignorados:** `.idea/`, `target/`, `.next/`, `node_modules/` (pré-existentes ou de build).

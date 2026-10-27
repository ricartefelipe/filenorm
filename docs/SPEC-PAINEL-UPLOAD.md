# Spec — Envio pelo painel, prévia, exportação e mapeamento CSV

Status: proposta, não implementada. Prioridade: P1 para pilotos com clientes não técnicos.

## Problema

Hoje o FileNorm só é usável por quem integra a API: o envio é via `curl` com API key e o resultado só sai em JSON. O usuário que mais sente a dor (o analista financeiro ou contábil) não consegue:

- enviar um arquivo;
- ver o que foi extraído antes de pagar;
- levar o resultado para a planilha ou o sistema dele;
- ajustar um CSV cujo cabeçalho não bate com os presets.

## Objetivos

1. Enviar um arquivo pelo navegador e acompanhar o processamento.
2. Ver uma **prévia gratuita e limitada** e o custo antes de confirmar a cobrança.
3. Exportar o resultado em CSV e XLSX, sem risco de injeção de fórmula.
4. Mapear colunas de um CSV desconhecido e salvar o mapeamento para reuso.

**Fora do escopo:** OCR, integração direta com ERP, edição manual de eventos, conciliação.

## Métricas de sucesso

- Um usuário novo envia e exporta o primeiro arquivo em menos de 5 minutos, sem ler a documentação.
- Pelo menos 80% dos jobs do piloto são enviados pelo painel (se o cliente não for técnico).
- Nenhuma exportação abre uma fórmula ativa no Excel/LibreOffice (teste automatizado).

## Fases

| Fase | Conteúdo | Esforço relativo |
|---|---|---|
| **1** | envio pelo painel, detalhe do job (eventos + issues), exportação CSV/XLSX, logout real | M (~1 semana) |
| **2** | prévia gratuita com confirmação de cobrança | M |
| **3** | mapeamento de colunas CSV salvo por conta | M |

Implementar a fase 1 antes do piloto; as fases 2 e 3 só se o piloto pedir.

---

## Fase 1 — Envio, detalhe, exportação

### API

O painel usa token de **sessão**; as rotas `/v1/jobs/*` aceitam apenas **API key** (`ApiKeyAuthFilter`). Para não misturar os dois mecanismos, as mesmas operações passam a ser expostas também sob `/v1/account/` (já protegidas por `SessionAuthFilter`), reutilizando o `JobService`, que já resolve a conta por `AccountContext`.

| Método | Rota (sessão) | Equivalente API key | Notas |
|---|---|---|---|
| `POST` | `/v1/account/jobs` | `POST /v1/jobs` | multipart `file`, `format`, `preset` |
| `GET` | `/v1/account/jobs/{id}` | `GET /v1/jobs/{id}` | |
| `GET` | `/v1/account/jobs/{id}/events` | idem | |
| `GET` | `/v1/account/jobs/{id}/issues` | idem | |
| `GET` | `/v1/account/jobs/{id}/export?type=csv\|xlsx` | `GET /v1/jobs/{id}/export` | **novo** nas duas superfícies |
| `DELETE` | `/v1/account/jobs/{id}` | idem | |

`GET /v1/account/jobs` (lista) já existe.

### Exportação

- Colunas: `data`, `valor` (com sinal: débito negativo), `moeda`, `direcao`, `contraparte`, `descricao`, `id_externo`, `chave_idempotencia`, `confianca`. `rawRef` **não** é exportado (minimização).
- CSV: UTF-8 com BOM (o Excel abre os acentos corretamente), separador `;`, decimal `,`. `?decimal=dot` gera `,` e `.`.
- **Neutralização de fórmula (obrigatória):** toda célula de texto que começa com `=`, `+`, `-`, `@`, tab ou CR recebe um prefixo `'`. Valores numéricos saem como número (XLSX) ou texto numérico validado (CSV), nunca como texto livre.
- XLSX: gerado em streaming (ex.: Apache POI `SXSSFWorkbook`), com uma aba `eventos` e uma aba `issues`.
- Disponível para jobs `succeeded` e `partial`; os demais retornam `409 job_not_finished`.
- Não é cobrado (o job já foi cobrado). `Content-Disposition` com o nome original saneado.

### Painel

- **`/app/jobs`:** área de arrastar ou selecionar arquivo, seletor de formato (auto por padrão) e de preset (só para CSV) e o custo estimado (`estimatedCredits` já vem na resposta). Depois do envio, a lista atualiza a cada 2 s enquanto houver job `queued`/`running`.
- **`/app/jobs/[id]` (nova):**
  - cabeçalho com status, formato, contagens e créditos;
  - abas **Eventos** (tabela paginada no cliente) e **Issues** (linha, motivo, detalhe);
  - botões **Exportar CSV**, **Exportar XLSX** e **Excluir** (com confirmação).
- Estados: vazio, carregando, erro com mensagem por `errorCode` (tabela de textos amigáveis, ex.: `insufficient_credits` → "Créditos insuficientes — compre um pacote" com link), sucesso, parcial (banner amarelo apontando para Issues).
- **Logout real:** `AppShell.onLogout` passa a chamar `POST /v1/auth/logout` antes de limpar o `localStorage`. Hoje a sessão continua válida no servidor depois de "Sair".
- Acessibilidade: input de arquivo com `label`, foco visível, tabela com `th scope`, status também em texto (não só cor), confirmação de exclusão acessível por teclado.

### Testes

- Integração: sessão de A não acessa job de B (404) nas novas rotas; API key **não** funciona em `/v1/account/jobs` e sessão **não** funciona em `/v1/jobs` (mantém a separação atual).
- Exportação: valores e sinais batem com `/events`; células `=1+1`, `+55…`, `-cmd`, `@SUM` saem neutralizadas; job `running` → 409; arquivo de 50 mil eventos sem estourar memória.
- Web: `tsc` + build; teste manual do fluxo completo, registrado no PR.

### Critérios de aceite

- [ ] Envio de OFX, CSV, CNAB e PDF pelo painel, com o status atualizando sozinho.
- [ ] Job `partial` mostra as issues com linha e motivo.
- [ ] CSV exportado abre no Excel/LibreOffice com acentos, valores corretos e nenhuma fórmula ativa.
- [ ] "Sair" invalida a sessão no servidor (`/v1/auth/me` → 401).
- [ ] Documentação (`USAGE.md`) atualizada com as novas rotas.

---

## Fase 2 — Prévia antes de cobrar

**Fluxo:** envio com `preview=true` → o job é processado até o novo status `awaiting_confirmation` (eventos e issues gravados, **sem cobrança**) → o painel mostra até **20 eventos**, todas as contagens, as issues e o custo exato (para PDF, o número real de páginas) → **Confirmar** cobra e libera eventos e exportação; **Descartar** apaga tudo.

Regras:
- Enquanto `awaiting_confirmation`, `/events` devolve no máximo 20 itens e a exportação retorna 409. Isso impede usar a prévia para obter o resultado de graça.
- A confirmação usa a mesma chave de cobrança (`job:<id>`), então confirmar duas vezes não cobra duas vezes. Saldo insuficiente na confirmação → 402, e o job continua aguardando.
- Prévias não confirmadas expiram em 24 h (removidas pela rotina de retenção).
- Limite de prévias pendentes por conta (ex.: 10) contra abuso de CPU.

Impacto no código: novo status na máquina de estados (`JobService.complete` passa a separar "gravar resultado" de "cobrar"); migration para `preview` e `expires_at`; o worker não muda.

---

## Fase 3 — Mapeamento de colunas CSV

**Fluxo:** um CSV cujo cabeçalho não casa com nenhum preset falha com `csv_columns_not_found`. O painel oferece **Mapear colunas** e mostra o cabeçalho e 5 linhas do arquivo. O usuário escolhe as colunas de data, valor (ou débito e crédito separados), descrição e o formato de data, e pode salvar o mapeamento com um nome.

- Tabela `csv_mappings` (por conta): nome, assinatura do cabeçalho (hash dos nomes normalizados), colunas, formato de data, sinal (valor único ou colunas separadas), versão.
- No envio, `preset=mapping:<id>`. Com `format=auto`, um mapeamento cuja assinatura casa com o cabeçalho é sugerido automaticamente.
- Versionado: editar cria uma nova versão; jobs antigos guardam a versão usada (reprocessamento reproduzível).
- Resolve o item do backlog "colunas separadas de débito/crédito".

## Riscos

| Risco | Mitigação |
|---|---|
| A prévia vira canal gratuito | limite de 20 eventos, sem exportação, expiração, limite por conta |
| Planilha com fórmula injetada | neutralização obrigatória + teste |
| XLSX grande consome memória | streaming (`SXSSF`) + limite atual de 50 mil registros |
| Duas superfícies de rota divergem | as duas chamam o mesmo `JobService`; os testes cobrem ambas |

## Decisões pendentes (proprietário)

1. Exportação disponível também via API key (útil para integradores), ou só no painel?
2. A prévia fica gratuita para sempre ou só no piloto?
3. Formato de exportação padrão do público-alvo (layout de importação de algum sistema contábil específico?). Definir com o cliente do piloto.

# FileNorm — Escopo do piloto

> Proposta de trabalho para validar o produto com um cliente real, dentro da fase 1 de [ESTRATEGIA.md](ESTRATEGIA.md). Pré-requisito: a fase 0 (entrevistas) aprovou o perfil. Preço, nome do cliente e termos jurídicos ficam com o proprietário; este documento não os define.

## Objetivo

Provar, com arquivos reais de **um** cliente, três coisas que hoje só estão demonstradas com dados sintéticos:

1. **Precisão:** os arquivos dos bancos dele viram eventos corretos (valor, sinal, data, contagem e totais).
2. **Economia:** o processo dele fica mais rápido do que hoje (horas por mês).
3. **Disposição a pagar:** ele aceita continuar pagando ao fim do piloto.

Sem os três, o piloto não autoriza vender para outros clientes.

## Perfil do cliente-alvo

Escolha **um** perfil. Misturar perfis no primeiro piloto dilui o aprendizado.

| Perfil | Por que encaixa | Sinal de que é o cliente certo |
|---|---|---|
| **Escritório contábil** (recomendado) | recebe extratos e retornos de **vários** clientes e bancos; o Open Finance não cobre bem esse fluxo | alguém digita ou reformata extratos toda semana |
| Empresa com cobrança por boleto | precisa conciliar **retorno CNAB 240** com o contas a receber | baixa os retornos do banco e confere à mão |
| Software house / ERP | quer embutir importação de extratos no próprio produto | já mantém parsers caseiros e sofre a cada mudança de banco |

Requisitos mínimos do cliente:
- pelo menos 20 arquivos por mês;
- no máximo 3 fontes (banco + formato) no piloto;
- uma pessoa disponível 1 hora por semana para conferência.

## Escopo

**Incluído**
- Até **3 fontes** de arquivo (ex.: retorno CNAB 240 de um banco, OFX de outro, CSV de um terceiro).
- Homologação de cada fonte: ajustar o parser ou preset, criar uma fixture **sintética** equivalente e registrar a fonte como suportada.
- Uso do painel e da API pelo cliente durante o período.
- As funcionalidades da fase 1 de [SPEC-PAINEL-UPLOAD.md](SPEC-PAINEL-UPLOAD.md), se o cliente não for técnico.
- Uma reunião semanal de 30 minutos e um relatório final.

**Fora do escopo**
- PDF escaneado (OCR), integração com ERP, conciliação automática.
- Mais de 3 fontes, ou fontes adicionadas depois da semana 2.
- SLA formal ou suporte fora do horário combinado.

## Cronograma (4 semanas)

| Semana | Atividades | Entregável |
|---|---|---|
| 0 — preparação | acordo de confidencialidade e tratamento de dados assinado; ambiente definido (ver abaixo); medição do processo atual (tempo por arquivo, erros por mês) | linha de base registrada |
| 1 — homologação | cliente envia de 3 a 5 arquivos por fonte; rodar, comparar com o extrato do banco, corrigir parser/preset; gerar fixtures sintéticas | fontes homologadas, testes de regressão no repositório |
| 2 — uso assistido | cliente processa o fluxo real; revisão conjunta de cada job `partial` | registro de cada issue e sua causa |
| 3 — uso autônomo | cliente usa sem acompanhamento; só medição | métricas da semana |
| 4 — fechamento | comparação com a linha de base; conversa de continuidade e preço | relatório final + decisão |

## Critérios de sucesso

| Critério | Meta | Como medir |
|---|---|---|
| Classificação | 100% dos registros de cada arquivo viram evento **ou** issue | `eventCount + rejectedCount + skippedCount` comparado com as linhas do arquivo |
| Exatidão | 0 divergência de valor, sinal ou data nos eventos aceitos | soma de créditos e débitos igual ao extrato do banco, por arquivo |
| Prova de fechamento | 100% `verified` nos arquivos que trazem saldo; todo `mismatch` explicado | `balanceCheck` de cada job |
| Rejeições legítimas | toda issue `rejected` corresponde a um problema real do arquivo | revisão semanal das issues |
| Confiabilidade | nenhum job em `failed` por erro do sistema (só por arquivo inválido) | `errorCode` dos jobs |
| Economia | redução de pelo menos 50% do tempo do processo atual | linha de base comparada com a semana 3 |
| Continuidade | o cliente aceita uma proposta paga | decisão na semana 4 |

**Critério de interrupção:** divergência de valor num arquivo aceito, sem causa identificada em 2 dias úteis.

## Dados e privacidade

- Extratos contêm dados pessoais e financeiros de terceiros. Antes de receber qualquer arquivo, é preciso ter assinado o acordo de confidencialidade e tratamento de dados (papéis de controlador e operador, finalidade, retenção, exclusão no fim).
- **Ambiente preferido:** instalação dedicada (`docker-compose.prod.yml`) no servidor do cliente ou num servidor exclusivo do piloto, com `FILENORM_RETENTION_DAYS` acordado e backup criptografado.
- **Arquivos reais nunca entram no repositório nem nos testes.** Para cada fonte homologada, criar uma fixture sintética com a mesma estrutura (leiaute, encoding, separadores) e valores fictícios.
- Ao final: `DELETE` dos jobs ou retenção vencida, mais a confirmação por escrito da exclusão.

## Papéis

| Quem | Responsabilidade |
|---|---|
| Proprietário (você) | contrato, ambiente, homologação das fontes, reunião semanal, relatório final |
| Cliente — responsável | fornecer arquivos, conferir resultados, medir tempo, decidir a continuidade |
| Cliente — TI (se houver) | servidor/rede no modelo instalado; integração via API |

## Riscos e mitigação

| Risco | Mitigação |
|---|---|
| O banco do cliente diverge do padrão FEBRABAN | homologação na semana 1, com tempo reservado para ajuste do parser |
| O cliente não consegue usar curl/API | fase 1 da spec do painel antes do piloto, ou escolher um cliente técnico |
| Arquivo com volume acima dos limites | ajustar `FILENORM_MAX_RECORDS`/páginas após medir |
| O piloto vira consultoria gratuita sem fim | escopo fechado de 3 fontes e 4 semanas, com decisão obrigatória na semana 4 |

## Relatório final (modelo)

1. Fontes homologadas: banco, formato, versão de leiaute e fixture criada.
2. Volume: arquivos, registros, eventos, issues por tipo.
3. Exatidão: totais conferidos por arquivo e divergências encontradas, com a correção.
4. Tempo: linha de base comparada com o piloto.
5. Pedidos do cliente, priorizados (entrada do backlog).
6. Decisão: continuidade, preço proposto e condições.

## Checklist antes de começar

- [ ] Perfil e cliente escolhidos
- [ ] Acordo de confidencialidade e tratamento de dados assinado
- [ ] Ambiente dedicado no ar, com backup agendado e restore testado (`docs/OPERACAO.md`)
- [ ] CI verde no commit que será instalado
- [ ] Linha de base medida
- [ ] Fase 1 do painel pronta, se o cliente não for técnico

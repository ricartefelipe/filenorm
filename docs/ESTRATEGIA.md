# FileNorm — Estratégia de produto

> Direção proposta, a ser confirmada pela descoberta (fase 0). Não inclui preço, metas de receita nem nomes de concorrentes; esses pontos dependem de pesquisa e de decisão do proprietário.

## Posição

**De:** "API que converte OFX/CSV/CNAB/PDF em JSON". É commodity: fácil de copiar, e o Open Finance entrega extrato estruturado sem arquivo.

**Para:** **"extrato e retorno bancário → lançamento contábil pronto para importar, com prova de que fechou"**, para **escritórios contábeis** e o financeiro de PMEs.

Por que esse público: ele recebe arquivos de dezenas de clientes e bancos, transforma isso à mão em lançamentos e só descobre os erros meses depois. Arquivo continua inevitável nesse fluxo (retornos CNAB, bancos fora do Open Finance, histórico, clientes que só mandam PDF).

## Os 5 pilares da vantagem

| # | Pilar | Por que é difícil de copiar | Estado |
|---|---|---|---|
| 1 | **Prova de fechamento por saldo** | transforma "confie no parser" em "está provado"; é a rede de segurança para heurísticas (PDF, OCR, IA) | **implementado** para CSV com saldo e PDF; OFX entre extratos no backlog |
| 2 | **Regras de classificação aprendidas por cliente** (descrição → conta contábil) | as regras acumuladas viram custo de troca | a fazer |
| 3 | **Matriz homologada bancos × sistemas contábeis**, cada célula com fixture e teste | trabalho operacional acumulado, não código | começa no piloto |
| 4 | **Fluxo de escritório**: várias empresas, fechamento mensal por empresa, deduplicação entre extratos sobrepostos | modelo de dados e UX específicos do nicho | a fazer (chave de idempotência já existe) |
| 5 | **Entrada sem esforço**: e-mail/pasta compartilhada; Open Finance como mais uma fonte | o cliente final do escritório não precisa usar nada | a fazer |

### Uso de IA (com limite claro)

- **Sim:** propor o mapeamento de um leiaute desconhecido (o parser determinístico executa e a prova de fechamento valida) e sugerir classificações revisáveis.
- **Não:** gerar valores, datas ou sinais diretamente.

## Roadmap com portões

| Fase | Entrega | Portão para avançar |
|---|---|---|
| **0. Descoberta** (2 semanas) | entrevistas (roteiro abaixo) | ≥ 3 escritórios confirmam a dor **e** aceitam pilotar |
| **1. Núcleo provado** | prova de fechamento ✅; CNAB 400; painel com envio e exportação ([spec](SPEC-PAINEL-UPLOAD.md), fase 1); exportação no layout do sistema contábil mais citado na fase 0 | piloto ([PILOTO.md](PILOTO.md)): 100% fechando onde houver saldo e −50% de tempo |
| **2. Escritório** | várias empresas por conta, regras de classificação, deduplicação entre extratos, entrada por e-mail | um escritório usa sozinho por 1 mês **e paga** |
| **3. Escala de cobertura** | mapeamento assistido por IA validado por saldo; 2º e 3º sistema contábil; Open Finance como fonte; prova de fechamento entre OFX consecutivos | homologar um banco novo leva horas, não dias |
| **4. Expansão** | conciliação retorno CNAB × títulos a receber, geração de remessa CNAB, OCR com revisão | pedido explícito de clientes pagantes |

**Regra:** nenhuma fase começa sem o portão anterior cumprido. Construir as fases 2 a 4 antes de a fase 0 confirmar a dor é o erro mais caro possível aqui.

## O que não fazer

- Competir com o Open Finance em "buscar extrato": usá-lo como fonte.
- Prometer "qualquer banco/qualquer PDF": vender a matriz homologada.
- Virar sistema contábil ou ERP: o produto é a ponte.
- Kubernetes, microsserviços, modelo de IA próprio: sem medição que justifique.

## Riscos

- **Cobertura é custo fixo:** bancos e sistemas contábeis mudam leiautes. Cada célula da matriz precisa de teste de regressão (já é o padrão do repositório).
- **Ferramentas existentes de importação de extratos para contabilidade:** a fase 0 precisa mapear o que os escritórios usam hoje e o que falta. A hipótese de diferencial é a prova de fechamento + regras aprendidas, e ela precisa ser **confirmada**, não presumida.
- **LGPD:** regras e várias empresas aumentam os dados guardados e o tempo de guarda. Contrato de tratamento de dados e retenção configurada antes do piloto.

---

## Fase 0 — Roteiro de entrevista (30–40 min)

**Objetivo:** confirmar (ou derrubar) a dor, o fluxo atual e a disposição a pagar. **Não** apresentar o produto nos primeiros 25 minutos; ouvir.

**Quem entrevistar:** sócio ou responsável pelo departamento contábil/fiscal de escritórios com 20+ empresas clientes; 5 a 8 entrevistas.

### 1. Contexto (5 min)
1. Quantas empresas o escritório atende? Quantas pessoas cuidam de lançamentos bancários?
2. Qual sistema contábil vocês usam? Ele importa extratos? Em que formato?

### 2. Fluxo atual (10 min)
3. Como os extratos chegam até vocês? (e-mail, WhatsApp, portal, o cliente dá acesso ao banco?)
4. Em que formatos chegam? Qual a proporção aproximada de OFX, CSV/planilha, PDF e retorno CNAB?
5. Me conte passo a passo o que acontece com um extrato desde que chega até virar lançamento.
6. Quanto tempo isso leva por empresa por mês? Quem faz?

### 3. Dor e erros (10 min)
7. Qual foi o último erro de lançamento bancário que deu problema? Como foi descoberto e quanto custou?
8. Como vocês conferem se um extrato foi lançado por completo? (procurar sinais de conferência de saldo manual)
9. O que mais irrita nesse processo hoje?
10. Já tentaram alguma ferramenta para isso? Por que continuaram ou pararam?

### 4. Classificação (5 min)
11. Como decidem em qual conta contábil cada linha entra? Isso se repete mês a mês para o mesmo cliente?
12. Quanto desse trabalho é repetido versus caso novo?

### 5. Valor e próximo passo (5–10 min)
13. Se X horas por mês fossem eliminadas *e* vocês tivessem a garantia de que o extrato fechou, quanto isso valeria? (deixar a pessoa dizer um número ou uma referência)
14. Quem decide a compra de uma ferramenta assim? Qual o processo?
15. Toparia um piloto de 4 semanas com 3 fontes de arquivo de um cliente seu?

### Sinais a registrar (por entrevista)

| Sinal | Forte | Fraco |
|---|---|---|
| Dor | cita horas/erros concretos e recentes | "seria bom ter" |
| Fluxo | arquivos chegando de várias formas, retrabalho mensal | tudo já entra automático pelo sistema contábil |
| Conferência | conferem saldo à mão ou já tiveram furo | não conferem e não tiveram problema |
| Compra | sabe quem decide e cita um valor | "precisaria ver" |
| Piloto | aceita com data | adia |

**Decisão ao fim da fase 0:**
- ≥ 3 sinais fortes de dor e compra → seguir para o piloto.
- Dor forte, mas no fluxo de **retorno CNAB/contas a receber** em vez de extratos → reposicionar para o perfil "empresa com cobrança".
- Sem dor forte → não construir as fases 1 e 2; reavaliar o produto.

Registre cada entrevista (data, perfil, respostas, sinais) num lugar fora do repositório, porque contém dados de terceiros.

# FileNorm — Resumo comercial

> Documento de apoio à decisão do proprietário. Não define preços nem cita clientes, e não substitui revisão jurídica.

## Para quem

- **Comprador:** quem decide a ferramenta de conciliação/financeiro numa empresa pequena ou média, num escritório contábil ou numa software house que integra extratos ao próprio sistema.
- **Usuário:** o desenvolvedor que integra a API, ou o analista que envia arquivos e confere os resultados.
- **Dor que dá para verificar:** cada banco exporta OFX, CSV, CNAB ou PDF num formato diferente. Hoje alguém reescreve parser ou digita lançamentos, e erros de valor, data ou duplicidade só aparecem na conciliação.

## O que está incluído (comprovado nesta versão)

- API com API key por conta: envio assíncrono, status, eventos normalizados, issues por linha e exclusão.
- OFX, CSV (presets genérico/nubank/inter), CNAB 240 de retorno de cobrança FEBRABAN (T/U) e PDF com texto por heurística. Os limites de cada formato estão em [USAGE.md](USAGE.md#4-formatos-suportados).
- **Prova de fechamento por saldo**: confere ao centavo, em CSV com saldo e em PDF, que saldo inicial + movimentos = saldo final, e aponta a linha onde a sequência quebra.
- Chave de idempotência por evento, status `partial` com explicação de cada linha rejeitada e nenhum descarte silencioso.
- Créditos pré-pagos com ledger auditável: o saldo nunca fica negativo, um job nunca é cobrado duas vezes e jobs com falha não são cobrados.
- Recuperação automática de jobs interrompidos, backup/restore ensaiado, métricas Prometheus.
- Painel: login, API keys, saldo, histórico de jobs e compra de pacotes pelo Stripe.

## O que não está incluído

- OCR de PDF escaneado, CNAB de remessa/extrato/pagamentos, leiautes de banco fora do padrão FEBRABAN.
- Mapeamento de colunas configurável pelo usuário, conciliação e integração com ERP.
- Exportação para planilha (por isso não há risco de injeção de fórmula hoje; ver backlog).
- SLA formal, suporte 24x7, certificações (LGPD/ISO/SOC). O produto tem controles técnicos, não certificações.

## Modalidades e condição de venda

| Modalidade | Situação | Condição para vender |
|---|---|---|
| **Instalação dedicada / serviço gerenciado para um cliente** (Compose num servidor do cliente ou do operador) | **GO piloto** | Piloto com arquivos reais do cliente (ver abaixo), contrato com escopo de formatos e responsabilidade de backup |
| **SaaS self-service com cobrança Stripe** | **NO-GO comercial por enquanto** | Stripe em modo de teste ponta a ponta (checkout real + webhook entregue), termos de uso e política de privacidade publicados, operação/backup agendados |
| **Venda de "qualquer banco, qualquer PDF"** | **Rejeitado** | Não prometer. Só vender formatos com fixture validada |

## Custos e dependências

- Infraestrutura: 1 VM pequena (Postgres + API + web + Caddy), disco para os originais e um destino de backup externo.
- Externos: Stripe (só no SaaS), SMTP transacional (magic link), domínio/TLS (Caddy + Let's Encrypt).
- Custo por arquivo: CPU curta (o parse é limitado a 10 MB, 50 mil registros e 100 páginas). Meça no piloto antes de definir preço.

## Onboarding e offboarding

- **Onboarding:** criar conta → API key → enviar 3 a 5 arquivos reais por banco/formato → revisar `/issues` com o cliente → fixar preset/formato por fonte.
- **Offboarding:** `DELETE /v1/jobs/{id}` (ou retenção configurada) remove eventos e originais. O ledger de créditos fica para auditoria. Desativar a conta revoga sessões e API keys.

## Demo curta (sintética)

1. `./scripts/free-demo.sh` → magic link na tela → criar API key.
2. Enviar `apps/api/src/test/resources/fixtures/nubank-conta.csv` (`preset=nubank`): mostra o job `partial` e a linha 4 rejeitada em `/issues`.
3. Enviar `cnab240-retorno-cobranca.ret` renomeado para `.txt`: o formato é detectado pelo conteúdo, com 2 liquidações e 1 ocorrência sem pagamento reportada.
4. Reenviar o mesmo OFX: os `idempotencyKey` são iguais.

## Objeções prováveis

- *"Como sei que não faltou nada?"* → quando o extrato traz saldo, o resultado vem com `verified` ou com a diferença exata em reais e a linha onde quebrou.
- *"E se o arquivo vier com erro?"* → o job vira `partial` com linha e motivo, e nada some em silêncio.
- *"Vocês cobram duas vezes se der problema?"* → não: a cobrança é única por job (chave única no ledger), a falha não é cobrada e a concorrência foi testada.
- *"Meu banco tem um CNAB diferente."* → só suportamos com fixture do seu banco; entra como item do piloto.
- *"Onde ficam meus extratos?"* → no servidor contratado, isolados por conta, com exclusão e retenção configuráveis.

## Piloto recomendado

Escopo completo em [PILOTO.md](PILOTO.md); funcionalidades de painel em [SPEC-PAINEL-UPLOAD.md](SPEC-PAINEL-UPLOAD.md).

- 1 cliente, de 2 a 4 semanas, com arquivos reais de no máximo 3 fontes.
- **Critério de saída:** 100% dos registros de cada arquivo classificados (evento ou issue), totais conferidos com o extrato do banco e zero divergência de valor/sinal/data nos arquivos aceitos.
- Os arquivos reais **não** entram no repositório: gerar fixtures sintéticas equivalentes para cada leiaute aprovado.

## Regras para chamar o produto de "comercialmente apto"

1. CI verde no commit publicado (GitHub Actions), não apenas local.
2. Pelo menos um piloto concluído com o critério acima.
3. Termos, privacidade e responsabilidade sobre os dados assinados/publicados.
4. Backup agendado e um restore ensaiado no ambiente do cliente.
5. Para o SaaS: compra real em modo de teste do Stripe creditando via webhook.

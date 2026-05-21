# Documento de Viabilidade — Projeto Halo

| Campo | Valor |
|---|---|
| Versão | 1.0 |
| Status | Draft |
| Última atualização | 2026-05-20 |
| Documentos relacionados | [PRD](./prd.md) · [Análise Técnica](./analise-tecnica.md) · [Tasks](./tasks.md) |

---

## 1. Problema Identificado

Pessoas que estão começando a se organizar financeiramente abandonam aplicativos de controle de gastos porque o registro exige **mudança de contexto** — abrir um app, navegar até a tela correta, preencher campos, escolher categoria. O esforço diário supera o benefício percebido a curto prazo, e o hábito não se forma.

O Halo ataca esse problema usando o WhatsApp como interface primária: o usuário escreve `Mercado 87,30` e a aplicação registra, categoriza e confirma — tudo de forma automática. Para isso funcionar sem exigir que o usuário escolha a categoria manualmente, é necessário que a aplicação **entenda linguagem natural e classifique o gasto de forma autônoma**. Esse é o papel central da IA no produto.

Fonte: [PRD §1.1 e §1.2](./prd.md), [Análise Técnica §1.1](./analise-tecnica.md).

---

## 2. Papel da IA no Produto

### 2.1 Onde a IA atua no produto

A IA (Google Gemini 2.5 Flash) atua em **dois pontos do fluxo principal** (Release 1, MVP WhatsApp):

**a) Parser de gastos via linguagem natural (implementado)**

Quando o usuário envia uma mensagem pelo WhatsApp, o backend aciona `GeminiClient.parseExpense()` ([`HttpGeminiClient.java`](../backend/src/main/java/dev/halo/ai/HttpGeminiClient.java)), que chama a API do Gemini com um prompt estruturado. O modelo devolve um JSON com quatro campos:

```json
{
  "description": "Mercado",
  "amount": 87.30,
  "category_hint": "Mercado",
  "occurred_at": "2026-05-18"
}
```

Esses campos são usados por `ExpenseService.createFromWhatsapp()` para persistir o gasto já categorizado — sem nenhuma interação adicional do usuário.

**b) Classificação de mensagens não-gasto (implementado)**

A IA também distingue mensagens que **não são gastos** (saudações, perguntas, comandos). Quando a mensagem não descreve uma despesa, o modelo devolve `{"error":"NOT_EXPENSE"}` e a mensagem é ignorada — o usuário não recebe confirmação de gasto.

**c) Resumos mensais via WhatsApp (planejado — R3)**

Na Release 3, a IA será usada para interpretar comandos como `"resumo abril"` e potencialmente para sugerir o tipo/cores do gráfico do relatório. Detalhes em [T-038](./tasks.md) e [Análise Técnica §9.3](./analise-tecnica.md).

### 2.2 Por que IA é necessária aqui

O canal de entrada é o WhatsApp — uma caixa de texto livre. Não há dropdown de categorias, não há formulário estruturado. O usuário pode escrever:

- `"Mercado 87,30"`
- `"Uber 25 ontem"`
- `"Gastei R$ 12,50 na farmácia hoje cedo"`
- `"Almoço 35.90"`

Todas essas frases descrevem um gasto, mas com formatos, vocabulário e informações completamente diferentes. Extrair de forma confiável: (1) o valor monetário, (2) uma categoria semântica, (3) a data mencionada — a partir de texto livre em português coloquial brasileiro — é um problema de **compreensão de linguagem natural** que não tem solução prática por regex ou regras fixas.

Uma abordagem puramente baseada em regras consegue extrair o valor numérico (como demonstra o [`AmountExtractor`](../backend/src/main/java/dev/halo/expense/AmountExtractor.java)), mas não consegue inferir que `"farmácia"` pertence à categoria `Saúde`, nem que `"uber"` pertence a `Transporte` — muito menos em todas as variações que um usuário real vai produzir.

### 2.3 Sem IA, o produto deixa de existir

A proposta de valor central do Halo é: **zero fricção no registro**. Isso significa que o usuário não deve precisar escolher uma categoria, confirmar campos ou preencher formulário. Qualquer solução que exija ação adicional do usuário após enviar a mensagem destrói o diferencial do produto.

Sem IA, a única alternativa seria pedir ao usuário que inclua a categoria na mensagem (ex.: `"Mercado 87,30 #alimentação"`) ou usar um menu de opções conversacional — o que recria exatamente a fricção que o produto pretende eliminar. A persona-alvo do Halo (ver [PRD §2.1](./prd.md)) já tentou e abandonou apps convencionais; uma UX conversacional estruturada teria o mesmo destino.

A IA não é uma feature adicional: ela é a camada que transforma texto livre em dado estruturado e, portanto, é **estrutural** ao funcionamento do produto.

---

## 3. Decisões Técnicas e Justificativa

### 3.1 Escolha do modelo: Gemini 2.5 Flash

O modelo escolhido é o **`gemini-2.5-flash`**, conforme registrado em [Análise Técnica ADR #9 e §9.1](./analise-tecnica.md) e configurado em [`GeminiProperties.java`](../backend/src/main/java/dev/halo/ai/GeminiProperties.java).

**Justificativas:**

| Critério | Decisão | Razão |
|---|---|---|
| Velocidade | Flash (não Pro) | NFR de latência < 3s p95 ([Análise Técnica §1.2](./analise-tecnica.md)); Flash é significativamente mais rápido |
| Custo | Flash | US$0,075/M tokens entrada + US$0,30/M tokens saída ([`GeminiPricing.java`](../backend/src/main/java/dev/halo/ai/GeminiPricing.java)); veja projeção na §4 |
| Suficiência | Flash | A tarefa é classificação de texto curto (~30–60 palavras) em categorias predefinidas — não exige raciocínio complexo do Pro |
| Suporte a JSON nativo | Gemini | O parâmetro `responseMimeType=application/json` força o modelo a devolver JSON válido, eliminando a necessidade de parsear markdown ao redor do JSON |
| Escopo MVP | Apenas texto | Áudio e imagem (foto de cupom fiscal) ficam fora do MVP ([PRD §4.2](./prd.md)) |

O `gemini-2.5-pro` está documentado como fallback possível para casos complexos ([Análise Técnica §9.1](./analise-tecnica.md)), mas não é usado no MVP.

### 3.2 Prompt Engineering Intencional

O prompt é construído pela função estática `HttpGeminiClient.buildPrompt()` ([`HttpGeminiClient.java`](../backend/src/main/java/dev/halo/ai/HttpGeminiClient.java), linha 109). As decisões de design do prompt:

**Estrutura do schema JSON declarado no prompt:**
```
{
  "description": string,
  "amount": number,
  "category_hint": string,
  "occurred_at": "YYYY-MM-DD" | null
}
```

**Decisões deliberadas:**

- **`temperature: 0.2`** — criatividade mínima. A tarefa é classificação, não geração criativa. Temperatura baixa aumenta determinismo e reprodutibilidade dos testes ([`HttpGeminiClient.java`](../backend/src/main/java/dev/halo/ai/HttpGeminiClient.java), linha 37).
- **`maxOutputTokens: 200`** — limite severo. A resposta esperada é um JSON pequeno (~40–60 tokens). O limite previne respostas longas e reduz custo ([`HttpGeminiClient.java`](../backend/src/main/java/dev/halo/ai/HttpGeminiClient.java), linha 38).
- **`responseMimeType: "application/json"`** — parâmetro nativo do Gemini que ativa o modo JSON, forçando saída sem markdown ou prosa ao redor.
- **Lista de categorias injetada no prompt** — o prompt recebe as categorias globais **e** as categorias customizadas do usuário como CSV. Isso restringe o `category_hint` ao vocabulário que o `ExpenseService` consegue resolver, evitando hints que não têm match no banco.
- **Sentinel `NOT_EXPENSE`** — o modelo é instruído a devolver `{"error":"NOT_EXPENSE"}` quando a mensagem não é um gasto. Isso é preferível a não responder ou a retornar JSON vazio, pois permite distinguir "não é gasto" (intenção legítima) de "erro técnico" (falha de rede ou JSON inválido).
- **Truncamento em 500 chars** — mensagens acima de 500 caracteres são cortadas antes de enviar ([`HttpGeminiClient.java`](../backend/src/main/java/dev/halo/ai/HttpGeminiClient.java), linha 103). Protege contra prompts anormalmente longos e limita custo ([Análise Técnica §9.4](./analise-tecnica.md)).
- **Prompt em português** — usuário escreve em pt-BR; o modelo é instruído em pt-BR. Isso melhora a acurácia em expressões coloquiais brasileiras (ex.: `"farmácia"`, `"uber"`, `"padaria"`).

### 3.3 Resiliência: Fallback Heurístico

O Gemini pode falhar por erro de rede, JSON inválido ou indisponibilidade. O RF-03 do PRD exige que **o gasto nunca seja perdido** por falha da IA:

> _"Se a IA falhar ou retornar JSON inválido, o Halo ainda registra o gasto com categoria 'Sem categoria' — nunca perde o registro."_ — [PRD RF-03](./prd.md)

A implementação no [`WhatsappExpenseParser`](../backend/src/main/java/dev/halo/expense/WhatsappExpenseParser.java) aplica o seguinte fluxo:

```
mensagem do usuário
       │
       ▼
GeminiClient.parseExpense()
       │
   ┌───┴──────────────┐
   │ resultado != null │ → usa resultado do Gemini (caminho feliz)
   └───┬──────────────┘
       │ null (NOT_EXPENSE ou falha)
       ▼
AmountExtractor.extract(texto)  ← regex em Java, sem IA
       │
   ┌───┴────────────────┐
   │ valor > 0 extraído │ → registra com "Sem categoria", texto como descrição, hoje como data
   └───┬────────────────┘
       │ nenhum valor
       ▼
   ignora mensagem (status IGNORED)
```

O [`AmountExtractor`](../backend/src/main/java/dev/halo/expense/AmountExtractor.java) suporta os formatos numéricos mais comuns em português: `87,30`, `87.30`, `R$5,50`, `1.234,56`, `1.500` (ponto como milhar). Essa camada não depende de nenhuma chamada externa — garante que uma queda do Gemini não interrompa o registro de gastos.

O fallback é registrado com `log.warn` para visibilidade operacional.

### 3.4 Observabilidade: Tabela `ai_log`

Toda chamada ao Gemini — bem ou mal sucedida — gera uma linha em `ai_log` ([`V1__init.sql`](../backend/src/main/resources/db/migration/V1__init.sql), linha 157). O [`AiLogService`](../backend/src/main/java/dev/halo/ai/AiLogService.java) persiste:

| Coluna | O que armazena | Por quê |
|---|---|---|
| `user_id` | UUID do usuário (nullable) | Liga o custo a cada conta; mede consumo por usuário |
| `model` | Nome do modelo (`gemini-2.5-flash`) | Permite comparar modelos futuramente |
| `prompt_hash` | SHA-256 do prompt (64 hex chars) | Rastreia prompts sem armazenar dados sensíveis do usuário |
| `tokens_in` / `tokens_out` | Contagem do `usageMetadata` da resposta | Base do cálculo de custo |
| `latency_ms` | Tempo decorrido em ms | Monitora o NFR de latência < 3s |
| `status` | `OK`, `INVALID_JSON`, `ERROR` | Alimenta a métrica §3.2 do PRD (taxa de erro ≤ 5%) |
| `cost_est` | Custo estimado em USD (`NUMERIC(10,6)`) | Monitora meta de custo < R$1,00/usuário/mês |

A transação do `AiLogService` usa `Propagation.REQUIRES_NEW` ([`AiLogService.java`](../backend/src/main/java/dev/halo/ai/AiLogService.java), linha 32) — o log é salvo mesmo que a transação do parser de gasto seja revertida. Isso garante que a observabilidade nunca seja perdida por falha posterior no pipeline.

---

## 4. Análise Custo/Benefício

### 4.1 Estimativa de custo por chamada

Preços do `gemini-2.5-flash` (fonte: [`GeminiPricing.java`](../backend/src/main/java/dev/halo/ai/GeminiPricing.java), linha 17–18):

- Entrada: US$0,075 por 1 milhão de tokens
- Saída: US$0,30 por 1 milhão de tokens

Em uma chamada típica:
- Prompt (template + lista de categorias + mensagem do usuário): ~150 tokens de entrada
- Resposta JSON (`description`, `amount`, `category_hint`, `occurred_at`): ~30–50 tokens de saída

Custo por chamada (estimativa):
```
150 × 0,000000075 + 40 × 0,00000030 = 0,00001125 + 0,000012 ≈ US$0,000023
```

### 4.2 Projeção mensal por usuário

Baseado na meta de engajamento do [PRD §3.1](./prd.md) (≥ 10 gastos/semana por usuário ativo):

| Cenário | Gastos/mês | Custo IA/mês (USD) | Custo IA/mês (BRL, ~R$5,50/USD) |
|---|---|---|---|
| Mínimo (engajamento base) | ~40 | US$0,0009 | ~R$0,005 |
| Típico (uso diário) | ~100 | US$0,0023 | ~R$0,013 |
| Intenso (múltiplos gastos/dia) | ~300 | US$0,0069 | ~R$0,038 |

**Todos os cenários ficam muito abaixo da meta de < R$1,00/usuário/mês** estabelecida na [Análise Técnica §1.2](./analise-tecnica.md). A meta poderia ser atingida mesmo com um fator de 25× mais chamadas do que o cenário intenso.

### 4.3 Benefício quantificável

O benefício direto é a eliminação da etapa de categorização manual. Sem IA:
- O usuário precisa de 3–5 interações adicionais por gasto (escolher categoria, confirmar)
- Com 10 gastos/semana, isso representa ~40 toques extras por semana
- A barreira de fricção foi identificada como a principal causa de abandono dos concorrentes ([PRD §1.1](./prd.md))

A relação custo/benefício é favorável: menos de R$0,04/mês em custo de IA para eliminar uma fricção que, sem a IA, inviabilizaria o produto.

---

## 5. Limitações Conhecidas

### 5.1 Limitações do Modelo

**NOT_EXPENSE e ERROR indistinguíveis no cliente atual:**
O [`WhatsappExpenseParser`](../backend/src/main/java/dev/halo/expense/WhatsappExpenseParser.java) recebe `null` tanto quando o modelo retorna `NOT_EXPENSE` (comportamento intencional) quanto quando ocorre uma falha técnica (erro de rede, JSON inválido). O comentário no código indica: _"o cliente atual não distingue os dois, ver T-013"_. Na prática, ambos os casos acionam o fallback heurístico — o gasto ainda é salvo se houver valor numérico, mas a mensagem pode ser mal classificada.

**Truncamento a 500 caracteres:**
Mensagens longas são cortadas antes de enviar ([`HttpGeminiClient.java`](../backend/src/main/java/dev/halo/ai/HttpGeminiClient.java), linha 103). Em descrições detalhadas (ex.: listagem de itens de mercado), informações relevantes para extração da data ou descrição podem ser perdidas.

**`category_hint` é correspondência textual, não semântica:**
O `ExpenseService` resolve a categoria pelo nome (`case-insensitive`), não por similaridade semântica. Se o modelo devolver `"Farmácia"` mas a categoria cadastrada for `"Saúde"`, o gasto cai em `"Sem categoria"`. A qualidade da categorização depende da compatibilidade entre o vocabulário do modelo e os nomes das categorias globais seedadas em [`V4__seed_global_categories.sql`](../backend/src/main/resources/db/migration/V4__seed_global_categories.sql).

**Contexto sem histórico do usuário:**
O prompt não inclui histórico de gastos anteriores nem preferências do usuário. O modelo classifica cada mensagem de forma isolada. Um usuário que habitualmente chama "Pet" seus gastos com animais sempre cairá em `"Sem categoria"` até criar uma categoria customizada.

### 5.2 Limitações Técnicas da Implementação

**Sem cache de classificação (previsto para T-043):**
Descrições idênticas (ex.: `"Uber 25"` enviada diariamente) chamam o Gemini a cada vez. O [T-043](./tasks.md) prevê um cache Caffeine in-memory por hash da descrição normalizada com TTL de 30 dias — mas ainda não está implementado.

**Sem retry no cliente Gemini:**
O [`HttpEvolutionClient`](../backend/src/main/java/dev/halo/whatsapp/HttpEvolutionClient.java) tem retry com backoff exponencial e circuit breaker via Resilience4j. O `HttpGeminiClient` ainda não tem esse mecanismo — uma falha transiente de rede resulta diretamente no fallback heurístico, sem tentativa de reprocessamento.

**Sem suporte a áudio e imagem:**
O MVP é exclusivamente texto ([Análise Técnica ADR #9](./analise-tecnica.md)). Fotos de cupom fiscal, áudios com valor dito, ou imagens de transferências bancárias são ignorados pelo pipeline atual.

### 5.3 Limitações de Produto/Escopo

**Usuário não pode corrigir a categoria via WhatsApp:**
A mensagem de confirmação instrui: _"Use a web para alterar"_. Não há comando de resposta para corrigir a categoria direto no chat — essa UX está no parking lot ([PRD §4.2](./prd.md) e [§14](./prd.md)).

**Múltiplos gastos em uma mensagem:**
Mensagens como `"Mercado 80 e farmácia 30"` não são explicitamente tratadas no MVP. O comportamento depende do que o modelo extrai — pode registrar apenas o primeiro item ou falhar. A [Questão em Aberto #3 do PRD](./prd.md) reconhece esse caso.

**Idioma fixo em pt-BR:**
O prompt e as categorias são exclusivamente em português. Usuários que escrevem em outro idioma não terão boa categorização.

---

## 6. Implementado vs. Proposta Futura

### 6.1 Implementado (R0 + R1)

Entregue nas tasks T-001 a T-017 (ver [tasks.md](./tasks.md)):

| Componente | Arquivo | Descrição |
|---|---|---|
| Interface do cliente IA | [`GeminiClient.java`](../backend/src/main/java/dev/halo/ai/GeminiClient.java) | Contrato do parser; permite substituição do provider |
| Implementação HTTP | [`HttpGeminiClient.java`](../backend/src/main/java/dev/halo/ai/HttpGeminiClient.java) | Chamada REST ao Gemini 2.5 Flash com prompt engineering |
| Resultado estruturado | [`ExpenseParseResult.java`](../backend/src/main/java/dev/halo/ai/ExpenseParseResult.java) | Record com 4 campos extraídos (description, amount, category_hint, occurred_at) |
| Pricing e log | [`GeminiPricing.java`](../backend/src/main/java/dev/halo/ai/GeminiPricing.java) · [`AiLogService.java`](../backend/src/main/java/dev/halo/ai/AiLogService.java) | Cálculo de custo e persistência em `ai_log` |
| Fallback heurístico | [`WhatsappExpenseParser.java`](../backend/src/main/java/dev/halo/expense/WhatsappExpenseParser.java) · [`AmountExtractor.java`](../backend/src/main/java/dev/halo/expense/AmountExtractor.java) | Pipeline Gemini → regex → ignora; nunca perde o registro |
| Schema de observabilidade | [`V1__init.sql`](../backend/src/main/resources/db/migration/V1__init.sql) | Tabela `ai_log` com tokens, latência, custo e status |
| Seed de categorias globais | [`V4__seed_global_categories.sql`](../backend/src/main/resources/db/migration/V4__seed_global_categories.sql) | 12 categorias + "Sem categoria" para o modelo ter onde mapear |
| Suite de testes | [`AiLogIntegrationTest`](../backend/src/test/java/dev/halo/ai/AiLogIntegrationTest.java) · [`HttpGeminiClientIntegrationTest`](../backend/src/test/java/dev/halo/ai/HttpGeminiClientIntegrationTest.java) · [`HttpGeminiClientUnitTest`](../backend/src/test/java/dev/halo/ai/HttpGeminiClientUnitTest.java) · [`AiLogServiceUnitTest`](../backend/src/test/java/dev/halo/ai/AiLogServiceUnitTest.java) · [`WhatsappExpenseParserTest`](../backend/src/test/java/dev/halo/expense/WhatsappExpenseParserTest.java) | Cobertura de prompt, truncamento, parsing, fallback, log de custo |

**Pendente de R1:** Smoke test com usuário externo real (T-018 — template em [`docs/qa/release-1-smoke.md`](./qa/release-1-smoke.md) preparado, ainda não executado).

### 6.2 Em planejamento (R2, R3, R4)

| Task | Componente | Descrição |
|---|---|---|
| T-038 | Parser de comandos | Gemini como fallback para reconhecer variações de `"resumo"` não cobertas por regex |
| T-041 | Geração de gráfico (Opção A) | Backend agrega dados deterministicamente em SQL; IA pode sugerir tipo/cores (QuickChart renderiza) |
| T-043 | Cache de classificação | Caffeine in-memory com hash da descrição normalizada; TTL 30 dias; elimina chamadas redundantes ao Gemini |
| T-044 | Rate limit e headers | Proteção contra abuso dos endpoints públicos (indireto: protege quota da API do Gemini) |

### 6.3 Parking lot (pós-MVP)

Itens documentados no [PRD §4.2](./prd.md) e [Análise Técnica §17](./analise-tecnica.md), sem task definida:

- **Áudio e imagem como entrada** — foto de cupom fiscal, áudio com valor falado; requereria modelos multimodais e aumento de custo por chamada.
- **Geração de gráfico por IA (Opção B)** — Gemini gerando imagem do gráfico a partir dos dados tabulares; descartada no MVP pelo risco de valores incorretos na imagem (trade-off documentado em [Análise Técnica §9.3](./analise-tecnica.md)).
- **Notificações proativas** — _"você gastou X em alimentação esta semana"_; exigiria IA ou regras de alerta por orçamento.
- **Edição de gasto via WhatsApp** — _"editar último"_, _"apagar último"_; requer parser de intenção adicional.
- **Exportação de relatórios com IA** — sumarização e insights em texto dos padrões de gasto mensais.

---

## 7. Próximos Passos Concretos

Seguindo o fluxo de workflow do [CLAUDE.md](../CLAUDE.md) e a ordem do [tasks.md](./tasks.md):

1. **T-018 — Smoke test da Release 1** (imediato)
   Executar o roteiro do [`docs/qa/release-1-smoke.md`](./qa/release-1-smoke.md) com um usuário externo real. Critério-chave: ≥ 80% de categorização correta em 5 gastos. Os resultados validarão ou não a acurácia da IA em contexto real.

2. **T-033 — Refinar seed de categorias globais** (início de R3)
   A migration [`V4__seed_global_categories.sql`](../backend/src/main/resources/db/migration/V4__seed_global_categories.sql) usa `keywords ARRAY[]::TEXT[]` vazios. Preencher os keywords relevantes por categoria (`mercado`, `supermercado`, `atacadão` para Mercado; `uber`, `ônibus`, `metrô` para Transporte) pode aumentar a taxa de match mesmo quando o `category_hint` retornado pelo modelo usa sinônimos.

3. **T-043 — Cache de classificação** (R3)
   Implementar cache Caffeine por hash de descrição normalizada. Estima-se eliminação de ~60–70% das chamadas ao Gemini para usuários com padrões de gasto repetitivos (ex.: `"Uber"` diário). Reduz custo e latência.

4. **Distinção NOT_EXPENSE vs. ERROR** (melhoria incremental)
   O comentário _"o cliente atual não distingue os dois, ver T-013"_ em [`WhatsappExpenseParser.java`](../backend/src/main/java/dev/halo/expense/WhatsappExpenseParser.java) indica uma lacuna de observabilidade. Diferenciar os dois casos no `ai_log` permitiria medir separadamente a taxa de "não-gastos legítimos" vs. falhas técnicas, melhorando a métrica §3.2 do PRD.

5. **Retry no cliente Gemini** (hardening)
   Adicionar retry com backoff exponencial ao `HttpGeminiClient` (análogo ao que já existe no [`HttpEvolutionClient.java`](../backend/src/main/java/dev/halo/whatsapp/HttpEvolutionClient.java)) para absorver falhas transientes de rede sem acionar imediatamente o fallback heurístico.

---

## 8. Conclusão

O Projeto Halo demonstra um uso de IA que é **estrutural ao produto, economicamente viável e tecnicamente responsável**.

A IA não é uma feature de polimento — ela é a camada que torna possível o canal de entrada pelo WhatsApp sem fricção. Sem o Gemini, a proposta de valor do produto deixa de existir, pois não há alternativa prática para classificar texto livre em categorias semânticas sem interação adicional do usuário.

A escolha do `gemini-2.5-flash` com `temperature=0.2`, resposta forçada em JSON e limites severos de tokens foi feita para maximizar determinismo e minimizar custo — projetado em menos de **R$0,04/usuário/mês** no cenário de uso intenso, muito abaixo da meta de R$1,00.

A implementação inclui mecanismos concretos de resiliência (fallback heurístico sem dependência de IA), observabilidade (tabela `ai_log` com custo, latência e status por chamada) e testabilidade (17 testes de integração e unitários cobrindo caminho feliz, NOT_EXPENSE, JSON inválido, erro HTTP, truncamento e fallback). Isso eleva a confiança técnica do projeto além do nível esperado para um MVP acadêmico.

O principal risco identificado é a acurácia da categorização em contexto real, que será validada pelo smoke test T-018. As decisões de arquitetura já contemplam esse risco: o fallback garante que nenhum gasto seja perdido por falha da IA, e a tabela `ai_log` provê os dados necessários para medir e melhorar a acurácia iterativamente.

---

> Este documento é complementar ao [PRD](./prd.md) e à [Análise Técnica](./analise-tecnica.md). Mudanças de modelo, estratégia de prompt ou escopo de IA devem ser refletidas nos três documentos conjuntamente.

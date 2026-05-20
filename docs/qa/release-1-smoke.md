# Smoke test — Release 1 (MVP WhatsApp)

> **Objetivo:** validar que um usuário externo (não-desenvolvedor) consegue
> cadastrar-se e registrar 5 gastos via WhatsApp sem ajuda — encerrando a Fase 1
> da [tasks.md](../tasks.md) e o critério de pronto da [Release 1 do PRD §9](../prd.md).
>
> Esta página é um **template** — preencha os campos `___` durante a execução
> e cole prints/logs onde indicado.

| Campo | Valor |
|---|---|
| Data da execução | ___ |
| Operador (quem roda o stack) | ___ |
| Usuário externo (quem manda mensagem) | ___ |
| Build do backend (`git rev-parse --short HEAD`) | ___ |
| Número do bot | ___ |
| Número do usuário externo | ___ |

---

## 1. Pré-requisitos do operador

Tudo isto deve estar pronto **antes** do usuário externo entrar em cena.

> **Atalho:** rode `scripts/smoke-prereqs.sh` para verificar/preparar
> automaticamente os 6 primeiros itens abaixo. Use `scripts/smoke-prereqs.sh
> --webhook https://<URL>/webhooks/evolution` depois de subir o ngrok para
> também configurar o webhook do Evolution.

- [ ] `infra/docker compose up -d` rodando (Postgres + Evolution Go).
- [ ] Instância `halo-bot` conectada ao Evolution (T-006). Confirme com:
  ```bash
  curl -H "apikey: $EVOLUTION_INSTANCE_TOKEN" $EVOLUTION_BASE_URL/instance/connectionState/halo-bot
  ```
  → deve retornar `"state":"open"`.
- [ ] `GEMINI_API_KEY` configurada em `backend/.env`.
- [ ] `EVOLUTION_INSTANCE_TOKEN` configurada em `backend/.env`.
- [ ] Backend rodando (`mvn spring-boot:run`) e `GET /actuator/health` devolvendo `{"status":"UP"}`.
- [ ] Webhook configurado para apontar para `https://<seu-host>/webhooks/evolution` (use ngrok/tailscale em dev).
- [ ] Categorias globais semeadas (Migrations **V3** + **V4** — verifique com
  `SELECT name FROM categories_global ORDER BY name;` no Postgres).

---

## 2. Roteiro para o usuário externo

Mostre apenas o número do bot — sem instruções escritas. Anote dificuldades.

| # | Cenário | Esperado | Tempo de resposta |
|---|---|---|---|
| 0 | Manda "Oi" para o bot | Bot pergunta "Olá! Eu sou o Halo. Qual seu nome?" | ___ |
| 1 | Responde o nome (≥ 2 chars) | Bot diz "Bem-vindo(a), <nome>!" | ___ |
| 2 | Registra "Mercado 87,30" | Confirmação `Registrado: ... R$ 87,30 → Mercado. Data: <DD/MM>. Use a web para alterar.` | ___ |
| 3 | Registra "Uber 25 ontem" | Confirmação com `→ Transporte. Data: <ontem em DD/MM>` | ___ |
| 4 | Registra "Almoço 35,90" | Confirmação com `→ Alimentação` | ___ |
| 5 | Registra "Farmácia 12,50" | Confirmação com `→ Saúde` ou `→ Outros` | ___ |
| 6 | Registra "Pet 18 ração" | Confirmação com `→ Sem categoria` (categoria inexistente) | ___ |

> O critério do PRD pede **≥ 80% de categorização correta entre os 5 gastos
> reais (linhas 2–6)** — isto é, **≥ 4 dos 5** devem cair numa categoria
> coerente (a especial "Sem categoria" é aceita só quando o gasto realmente
> não cabe nas globais existentes).

---

## 3. Resultados

### 3.1 Cadastro (RF-01)

- [ ] Usuário entendeu a pergunta inicial sem ajuda.
- [ ] Nome foi aceito na primeira tentativa.
- [ ] Mensagem de boas-vindas chegou em até **3s** após a resposta.

**Observações livres:** ___

### 3.2 Registro (RF-03 / RF-04)

| # | Mensagem | Categoria sugerida | Acerto? (S/N) | Latência ida-e-volta |
|---|---|---|---|---|
| 2 | Mercado 87,30 | ___ | ___ | ___ |
| 3 | Uber 25 ontem | ___ | ___ | ___ |
| 4 | Almoço 35,90 | ___ | ___ | ___ |
| 5 | Farmácia 12,50 | ___ | ___ | ___ |
| 6 | Pet 18 ração | ___ | ___ | ___ |

- **Acertos:** ___ / 5
- **Taxa de categorização correta:** ___ %
- **Latência média (gasto → confirmação):** ___ ms — *meta NFR §1.2: < 3000ms p95*

### 3.3 Observabilidade

```sql
-- Cole o resultado destas queries após a execução:
SELECT count(*) FROM whatsapp_messages WHERE direction = 'IN';
SELECT count(*) FROM expenses WHERE user_id = (SELECT id FROM users WHERE phone = '+5547___');
SELECT status, count(*) FROM ai_log GROUP BY status;
SELECT round(avg(latency_ms)) AS avg_ms, round(sum(cost_est)::numeric, 6) AS total_usd
  FROM ai_log;
```

### 3.4 Prints / logs

Cole prints da conversa no WhatsApp e trechos relevantes de
`logs/backend.log` (procure por `Mensagem registrada`, `Confirmação enviada`,
`Fallback heurístico`, `Telefone inválido`).

---

## 4. Critérios de aceitação (T-018)

- [ ] Usuário consegue se cadastrar (RF-01) **sem ajuda externa**.
- [ ] Ao menos **4 dos 5 gastos foram categorizados corretamente pela IA** (≥ 80%).
- [ ] **Tempo de resposta médio do registro foi < 3s.**
- [ ] **Documento de relato preenchido com prints/logs.**

---

## 5. Bugs encontrados

| # | Severidade | Descrição | Issue/PR |
|---|---|---|---|
| 1 | ___ | ___ | ___ |

---

## 6. Veredito da Release 1

- [ ] ✅ **Pronta para piloto** (todos os critérios verdes; bugs leves anotados em issues).
- [ ] ⚠️ **Pronta com ressalvas** (1 critério amarelo — ver §5).
- [ ] ❌ **Bloqueada** (algum critério não atendido — ver §5).

**Assinatura do operador:** ___ — **Data:** ___

# Backlog — Projeto Halo

> Tarefas de implementação derivadas do [prd.md](./prd.md) e da [analise-tecnica.md](./analise-tecnica.md).
> Cada tarefa tem **entrega fechada** (algo demonstrável ao final), critérios de aceitação verificáveis, e referência ao RF do PRD quando aplicável.

| Campo | Valor |
|---|---|
| Versão | 1.0 |
| Última atualização | 2026-05-18 |
| Releases | 5 (R0 Setup, R1 MVP WhatsApp, R2 Web mínima, R3 Relatórios, R4 Hardening) |
| Total de tarefas | 50 |

## Convenções

- **ID**: `T-XXX` (sequencial, **imutável** mesmo se a tarefa for cancelada).
- **Componente**: `infra` | `backend` | `frontend` | `db` | `docs` | `qa`.
- **RF(s)**: referência aos requisitos funcionais do PRD (§6). Tarefas de infra/transversal podem não ter RF associado.
- **Estimativa**: em dias úteis de trabalho focado (0.25d = 2h, 0.5d = 4h, 1d = 8h).
- **Depende de**: IDs de tarefas que precisam estar concluídas antes (ou `—` se não houver).
- **Entrega**: o artefato concreto que existe ao final (PR mergeado, container subindo, endpoint respondendo, etc.).

---

# Release 0 — Setup do Repositório e Ambiente Dev

> **Objetivo:** ter o monorepo funcional, com docker-compose subindo Postgres + Evolution GO + backend "hello world" + frontend "hello world", e CI rodando.
> **Critério de pronto:** desenvolvedor consegue clonar o repo, rodar `docker compose up` e ver os 4 serviços respondendo.
> **Estimativa total:** ~4 dias.

---

### T-001 — Estrutura inicial do monorepo
- **Componente:** infra
- **RF(s):** —
- **Estimativa:** 0.25d
- **Depende de:** —

**Entrega:** estrutura de pastas `backend/`, `frontend/`, `infra/`, `docs/` criada conforme [analise-tecnica.md §5](./analise-tecnica.md), com `.gitignore` raiz, `LICENSE` e `README.md` raiz com instruções básicas de setup.

**Critérios de aceitação:**
- [ ] Pastas `backend/`, `frontend/`, `infra/`, `docs/` existem na raiz.
- [ ] `.gitignore` cobre Java (target/, *.class), Node (node_modules/, dist/), env files (.env, .env.local) e IDE (.idea/, .vscode/).
- [ ] `README.md` raiz explica em 1-2 parágrafos o que é o Halo e linka prd.md e analise-tecnica.md.

---

### T-002 — Scaffold backend Spring Boot
- **Componente:** backend
- **RF(s):** —
- **Estimativa:** 0.5d
- **Depende de:** T-001

**Entrega:** projeto Spring Boot 3.3.x + Java 21 inicial em `backend/`, com `pom.xml`, `HaloApplication.java`, `application.yml` (dev) e endpoint `/actuator/health` respondendo 200.

**Critérios de aceitação:**
- [ ] `mvn spring-boot:run` sobe o app em `localhost:8080`.
- [ ] Dependências do `pom.xml`: Spring Web, Data JPA, Security, Actuator, Validation, Flyway, Lombok, MapStruct, JJWT 0.12.x, springdoc-openapi, Bucket4j, Testcontainers, JUnit 5.
- [ ] `application.yml` lê `SPRING_DATASOURCE_*` de variáveis de ambiente.
- [ ] `/actuator/health` retorna `{"status":"UP"}`.

---

### T-003 — Scaffold frontend Vite + React + Tailwind
- **Componente:** frontend
- **RF(s):** —
- **Estimativa:** 0.5d
- **Depende de:** T-001

**Entrega:** projeto React 18 + TypeScript + Vite + TailwindCSS 3 em `frontend/`, com `tailwind.config.js`, `vite.config.ts`, rota raiz exibindo "Halo".

**Critérios de aceitação:**
- [ ] `npm run dev` sobe em `localhost:5173` mostrando "Halo".
- [ ] Tailwind ativo (testar com uma classe utility, ex: `text-blue-500`).
- [ ] Dependências instaladas: React Router v6, TanStack Query, React Hook Form, Zod, Axios, dayjs, Recharts, Zustand, vite-plugin-pwa.
- [ ] `.env.example` no `frontend/` documenta `VITE_API_BASE_URL`.

---

### T-004 — docker-compose dev com Postgres e Evolution GO
- **Componente:** infra
- **RF(s):** —
- **Estimativa:** 1d
- **Depende de:** T-001

**Entrega:** `infra/docker-compose.yml` que sobe Postgres 16 + Evolution GO em rede docker compartilhada, com volume persistente para o banco e `.env.example` documentando todas as variáveis.

**Critérios de aceitação:**
- [ ] `docker compose up -d` sobe `postgres` e `evolution-go` saudáveis.
- [ ] Postgres acessível em `localhost:5432` (banco `halo`, user `halo`).
- [ ] Evolution GO acessível em `localhost:8080` com docs/swagger abrindo.
- [ ] Volume `halo-pgdata` persiste dados entre restarts.
- [ ] `infra/.env.example` lista todas as vars (sem segredos reais).

---

### T-005 — Migration inicial Flyway (schema base)
- **Componente:** backend/db
- **RF(s):** —
- **Estimativa:** 1d
- **Depende de:** T-002, T-004

**Entrega:** `V1__init.sql` em `backend/src/main/resources/db/migration/` criando todas as tabelas do schema da [analise-tecnica.md §6](./analise-tecnica.md): `users`, `categories_global`, `categories`, `expenses`, `otp_codes`, `refresh_tokens`, `whatsapp_messages`, `conversation_state`, `ai_log`.

**Critérios de aceitação:**
- [ ] Flyway aplica a migration ao subir o backend, sem erros.
- [ ] Todas as tabelas, FKs, UNIQUE constraints e índices descritos em §6 existem.
- [ ] PKs usam UUID; `expenses.amount` é `numeric(12,2)`; `whatsapp_messages.evolution_msg_id` é UNIQUE.
- [ ] `expenses.source` é enum com valores `WHATSAPP`, `WEB`.
- [ ] `expenses` tem coluna `deleted_at` (nullable) para soft delete.

---

### T-006 — Conectar instância halo-bot ao Evolution GO
- **Componente:** infra
- **RF(s):** —
- **Estimativa:** 0.5d
- **Depende de:** T-004

**Entrega:** instância `halo-bot` criada na Evolution GO via API e conectada a um número de WhatsApp de testes (descartável). QR code escaneado. Documentação do passo a passo em `docs/setup-evolution.md`.

**Critérios de aceitação:**
- [ ] Instância `halo-bot` aparece como `connected` na API Evolution.
- [ ] Envio de mensagem de teste via `POST /message/sendText/halo-bot` funciona.
- [ ] `docs/setup-evolution.md` explica como criar a instância e escanear o QR (com prints/snippets de curl).

---

### T-007 — CI básico (lint + build backend e frontend)
- **Componente:** infra
- **RF(s):** —
- **Estimativa:** 0.5d
- **Depende de:** T-002, T-003

**Entrega:** workflow GitHub Actions (`.github/workflows/ci.yml`) que em cada PR/push roda: `mvn verify` no backend, `npm run lint && npm run build` no frontend.

**Critérios de aceitação:**
- [ ] Workflow dispara em push e em PR para `main`.
- [ ] Falha do build/lint quebra o check do PR (não permite merge sem aprovação).
- [ ] Cache de dependências (Maven `.m2` e `node_modules`) configurado.

---

# Release 1 — MVP WhatsApp

> **Objetivo:** usuário externo consegue se cadastrar pelo WhatsApp e registrar 5 gastos com categorização automática, sem ajuda.
> **Critério de pronto:** RF-01, RF-02, RF-03, RF-04 implementados + ≥ 75% de categorização correta em amostra de 20 mensagens reais.
> **Estimativa total:** ~8 dias.

---

### T-008 — Endpoint POST /webhooks/evolution com validação apikey
- **Componente:** backend
- **RF(s):** RF-02
- **Estimativa:** 1d
- **Depende de:** T-005

**Entrega:** controller que recebe o payload da Evolution, valida header `apikey`, e responde 200 (ainda sem processar). Lib `EvolutionPayloadDto` modela o JSON.

**Critérios de aceitação:**
- [ ] `POST /webhooks/evolution` aceita o JSON descrito em [analise-tecnica.md §8.2](./analise-tecnica.md).
- [ ] Sem header `apikey` (ou com valor errado) retorna 401.
- [ ] Mensagens com `fromMe=true` são ignoradas (responde 200 sem processar).
- [ ] DTO com Jackson deserializa `data.key.id`, `data.key.remoteJid`, `data.message.conversation`, `data.pushName`, `data.messageTimestamp`.

---

### T-009 — Persistência idempotente de whatsapp_messages
- **Componente:** backend
- **RF(s):** RF-01 (critério de duplicação)
- **Estimativa:** 0.5d
- **Depende de:** T-008

**Entrega:** ao receber webhook, persistir registro em `whatsapp_messages` usando `evolution_msg_id` como chave de idempotência. Mensagem duplicada não dispara processamento.

**Critérios de aceitação:**
- [x] Receber o mesmo `evolution_msg_id` 2x cria apenas 1 linha em `whatsapp_messages`.
- [x] Telefone normalizado para E.164 antes de salvar.
- [x] `direction = 'IN'` em mensagens recebidas.

---

### T-010 — Identificação de usuário por telefone (E.164)
- **Componente:** backend
- **RF(s):** RF-02
- **Estimativa:** 0.5d
- **Depende de:** T-009

**Entrega:** classe `PhoneNumberService` que normaliza qualquer entrada (com/sem `+`, com/sem `@s.whatsapp.net`) para E.164, e `UserService.findOrNull(phone)`.

**Critérios de aceitação:**
- [x] Testes unitários cobrem entradas: `+5547999999999`, `5547999999999`, `5547999999999@s.whatsapp.net`.
- [x] Telefone inválido lança exceção tratada (resposta apropriada no webhook).
- [x] `findOrNull(phone)` retorna `User` ou `null`.

---

### T-011 — Cadastro conversacional (estado AWAITING_NAME)
- **Componente:** backend
- **RF(s):** RF-01
- **Estimativa:** 1d
- **Depende de:** T-010

**Entrega:** fluxo completo: usuário novo recebe pergunta "Qual seu nome?", próxima mensagem dele é salva como nome, conta é criada, mensagem de boas-vindas enviada.

**Critérios de aceitação:**
- [x] Primeira mensagem de telefone desconhecido grava `conversation_state` com `state=AWAITING_NAME` e responde via Evolution "Qual seu nome?".
- [x] Segunda mensagem cria `user` (`name`, `phone`), deleta `conversation_state`, e envia "Bem-vindo(a), <nome>!".
- [x] `conversation_state.expires_at = now() + 15min`; estado expirado é descartado e a próxima mensagem reinicia o fluxo.
- [x] Nome com menos de 2 caracteres → reenvia a pergunta.

---

### T-012 — EvolutionClient para envio de mensagens (com retry)
- **Componente:** backend
- **RF(s):** RF-04
- **Estimativa:** 0.5d
- **Depende de:** T-006

**Entrega:** `EvolutionClient` (Spring RestClient) com método `sendText(phone, text)` e retry exponencial (3 tentativas: 1s/3s/9s) em erros 5xx. Circuit breaker via Resilience4j.

**Critérios de aceitação:**
- [x] `sendText` chama `POST /message/sendText/{instance}` corretamente.
- [x] Erros 5xx disparam retry; erros 4xx falham imediatamente.
- [x] Circuit breaker abre após 5 falhas consecutivas; meio-aberto após 30s.
- [x] Testes unitários com mock do RestClient cobrem o caminho feliz e o de retry.

---

### T-013 — GeminiClient e prompt do parser de gasto
- **Componente:** backend
- **RF(s):** RF-03
- **Estimativa:** 1d
- **Depende de:** T-005

**Entrega:** `GeminiClient.parseExpense(text, userCategories)` que chama `gemini-2.5-flash` com `response_mime_type=application/json` e retorna `ExpenseParseResult` ou `null` (em caso de erro/`NOT_EXPENSE`).

**Critérios de aceitação:**
- [x] Prompt segue o template da [analise-tecnica.md §9.2](./analise-tecnica.md), injetando categorias globais + customizadas do usuário.
- [x] `temperature=0.2`, `max_output_tokens=200`.
- [x] Retorno bem-sucedido contém: `description`, `amount`, `category_hint`, `occurred_at` (opcional).
- [x] JSON inválido ou `{"error":"NOT_EXPENSE"}` resultam em `null`.
- [x] Mensagens > 500 chars são truncadas antes de enviar.

---

### T-014 — ExpenseService: persistência + resolução de categoria
- **Componente:** backend
- **RF(s):** RF-03
- **Estimativa:** 1d
- **Depende de:** T-013

**Entrega:** `ExpenseService.createFromWhatsapp(user, parseResult, rawMessage)` que resolve a categoria (match por nome em globais/customizadas) e persiste o gasto.

**Critérios de aceitação:**
- [x] Match de categoria é case-insensitive, primeiro busca em customizadas do user, depois em globais.
- [x] Se `category_hint` não bate com nenhuma, usa "Sem categoria" (categoria global especial).
- [x] `expense.source = 'WHATSAPP'`, `expense.raw_message = <texto original>`, `expense.occurred_at = parseResult.occurred_at || today()`.
- [x] `expense.amount` validado > 0; valores ≤ 0 são rejeitados com log.

---

### T-015 — Mensagem de confirmação formatada (pt-BR)
- **Componente:** backend
- **RF(s):** RF-04
- **Estimativa:** 0.5d
- **Depende de:** T-014, T-012

**Entrega:** `ExpenseConfirmationFormatter` que monta a mensagem de confirmação com descrição, valor (`R$ X.XXX,YY`), categoria e data (`DD/MM`).

**Critérios de aceitação:**
- [x] Mensagem segue o padrão: `Registrado: <descrição> R$ <valor> → <categoria>. Data: <DD/MM>. Use a web para alterar.`
- [x] Valor formatado em pt-BR com separador de milhar `.` e decimal `,`.
- [x] Categoria fallback exibe "Sem categoria" exatamente.
- [x] Confirmação é enviada via `EvolutionClient.sendText`.

---

### T-016 — Fallback de IA: registrar gasto com "Sem categoria" em caso de falha
- **Componente:** backend
- **RF(s):** RF-03
- **Estimativa:** 0.5d
- **Depende de:** T-014

**Entrega:** quando `GeminiClient.parseExpense` retorna `null` por erro técnico (não por `NOT_EXPENSE`), ainda assim o gasto é registrado com categoria "Sem categoria", usando heurística simples (regex de número para extrair valor) e a mensagem original como descrição.

**Critérios de aceitação:**
- [x] Mensagem como "Mercado 87,30" com IA fora do ar ainda persiste o gasto.
- [x] Heurística regex extrai valor decimal (`,` ou `.` como separador).
- [x] Se nem o regex acha um valor, a mensagem é considerada `NOT_EXPENSE` e ignorada.
- [x] Log de WARN registra que o fallback foi usado.

---

### T-017 — Logging em ai_log de chamadas ao Gemini
- **Componente:** backend
- **RF(s):** RF-03 (métrica §3.2)
- **Estimativa:** 0.5d
- **Depende de:** T-013

**Entrega:** toda chamada ao `GeminiClient` registra linha em `ai_log` com modelo, hash do prompt, tokens in/out (do response da Gemini), latência e status (`OK`, `INVALID_JSON`, `ERROR`).

**Critérios de aceitação:**
- [x] `ai_log.user_id` preenchido quando o user é conhecido.
- [x] `prompt_hash` é SHA-256 do prompt (não armazenamos o texto completo).
- [x] Custo estimado (`cost_est`) calculado com base nos tokens e preço documentado (constante).
- [x] Status reflete: sucesso, JSON inválido, erro de rede.

---

### T-018 — Smoke test E2E Release 1 com usuário externo
- **Componente:** qa
- **RF(s):** RF-01, RF-02, RF-03, RF-04
- **Estimativa:** 0.5d
- **Depende de:** T-015, T-016, T-017

**Entrega:** roteiro executado por um(a) usuário externo (não-desenvolvedor) registrando 5 gastos. Resultados documentados em `docs/qa/release-1-smoke.md` com taxa de categorização correta.

**Critérios de aceitação:**
- [ ] Usuário consegue se cadastrar (RF-01) sem ajuda externa.
- [ ] Ao menos 4 dos 5 gastos foram categorizados corretamente pela IA (≥ 80%).
- [ ] Tempo de resposta médio do registro foi < 3s.
- [ ] Documento de relato preenchido com prints/logs.

---

# Release 2 — Web Mínima (Login, Dashboard, CRUD de Lançamentos)

> **Objetivo:** usuário consegue logar via OTP, ver seu dashboard, listar/editar/excluir lançamentos.
> **Critério de pronto:** RF-05, RF-09, RF-11, RF-12, RF-13, RF-14, RF-17 + testado em Android e iOS reais.
> **Estimativa total:** ~10 dias.

---

### T-019 — Endpoint POST /auth/otp/request
- **Componente:** backend
- **RF(s):** RF-09
- **Estimativa:** 1d
- **Depende de:** T-012

**Entrega:** endpoint que gera código de 6 dígitos, salva hash bcrypt em `otp_codes` (TTL 5min), envia via WhatsApp com mensagem incluindo "Nunca compartilhe este código.".

**Critérios de aceitação:**
- [ ] Telefone normalizado em E.164 antes de buscar/criar.
- [ ] Se telefone não existe em `users`, ainda assim envia OTP (cadastro implícito não existe nessa rota — assumimos pré-cadastro via WhatsApp).
- [ ] Código gerado com `SecureRandom` (6 dígitos numéricos).
- [ ] Cooldown de 60s por telefone via Bucket4j em memória; 429 em caso de excesso.
- [ ] Mensagem enviada via Evolution contém o código e aviso de segurança.
- [ ] Resposta 200 sem revelar se o telefone existe ou não.

---

### T-020 — Endpoint POST /auth/otp/verify (emite JWT + refresh)
- **Componente:** backend
- **RF(s):** RF-09
- **Estimativa:** 1d
- **Depende de:** T-019

**Entrega:** valida código (compara bcrypt), checa TTL e tentativas, emite access token JWT (15min) e refresh token (UUID opaco, 30d) — refresh em cookie `httpOnly; Secure; SameSite=Strict`.

**Critérios de aceitação:**
- [ ] Código correto + dentro do TTL → 200 com access token no body e refresh em cookie.
- [ ] Código errado incrementa `attempts`; após 5 tentativas, código é invalidado (`used_at` setado).
- [ ] Código expirado → 401.
- [ ] Refresh token persistido em `refresh_tokens` com hash, `user_agent`, `ip`.

---

### T-021 — Filtro JWT + endpoint /auth/refresh
- **Componente:** backend
- **RF(s):** RF-09
- **Estimativa:** 1d
- **Depende de:** T-020

**Entrega:** filtro do Spring Security valida access token em todas as rotas autenticadas. Endpoint `POST /auth/refresh` lê o cookie, rotaciona o refresh token (revoga o anterior), emite novo access.

**Critérios de aceitação:**
- [ ] Rota protegida sem JWT válido retorna 401.
- [ ] JWT inválido/expirado retorna 401.
- [ ] `/auth/refresh` exige cookie válido; revoga o anterior e emite novo.
- [ ] Refresh token revogado/expirado retorna 401.
- [ ] Configuração `SecurityFilterChain` definida; CORS configurado para `VITE_API_BASE_URL`.

---

### T-022 — Endpoint DELETE /auth/sessions/current (logout)
- **Componente:** backend
- **RF(s):** RF-11
- **Estimativa:** 0.25d
- **Depende de:** T-021

**Entrega:** endpoint que revoga o refresh token associado ao cookie e limpa o cookie.

**Critérios de aceitação:**
- [ ] `refresh_tokens.revoked_at` setado.
- [ ] Cookie de refresh limpo na resposta.
- [ ] Próxima chamada com o mesmo refresh retorna 401.

---

### T-023 — Endpoints GET /me e PATCH /me
- **Componente:** backend
- **RF(s):** RF-17
- **Estimativa:** 0.5d
- **Depende de:** T-021

**Entrega:** dois endpoints autenticados: retornar perfil (`id, name, phone, created_at`) e atualizar `name`.

**Critérios de aceitação:**
- [ ] `GET /me` retorna o usuário autenticado.
- [ ] `PATCH /me` aceita `{ "name": "..." }`; valida tamanho ≥ 2 chars.
- [ ] `phone` é imutável (não pode ser alterado via PATCH).

---

### T-024 — Endpoints CRUD /expenses
- **Componente:** backend
- **RF(s):** RF-05, RF-13, RF-14
- **Estimativa:** 1.5d
- **Depende de:** T-021, T-014

**Entrega:** endpoints `GET /expenses` (paginado + filtros), `POST /expenses`, `GET /expenses/{id}`, `PATCH /expenses/{id}`, `DELETE /expenses/{id}` (soft delete).

**Critérios de aceitação:**
- [ ] `GET /expenses` aceita query params: `from`, `to`, `category_id`, `q` (busca em description), `page`, `size`. Ordena por `occurred_at desc`.
- [ ] `POST /expenses` valida: description (1-200 chars), amount > 0, category_id existente para o user, occurred_at obrigatório. `source = 'WEB'`.
- [ ] `PATCH /expenses/{id}` aceita os mesmos campos do POST como opcionais; verifica que o gasto pertence ao user.
- [ ] `DELETE /expenses/{id}` faz soft delete (`deleted_at = now()`); gastos deletados não aparecem em GET.
- [ ] Todas as rotas exigem JWT; tentativa de acessar gasto de outro user retorna 404 (não 403, para não vazar existência).

---

### T-025 — Layout base do frontend + Bottom Navigation
- **Componente:** frontend
- **RF(s):** RF-12 (mobile-first)
- **Estimativa:** 1d
- **Depende de:** T-003

**Entrega:** `App.tsx` com React Router v6, layout mobile-first, bottom navigation fixa com 4 ícones (Dashboard, Lançamentos, Categorias, Perfil). Componentes base: Button, Input, Select, Modal, BottomSheet, Toast.

**Critérios de aceitação:**
- [ ] Bottom nav visível em todas as páginas autenticadas; oculta no /login.
- [ ] Tema/paleta minimalista (1 primária + 1 acento + neutros) definidos no `tailwind.config.js`.
- [ ] Tipografia Inter (ou system stack) com 4 escalas.
- [ ] Componentes base têm props básicas e estado de loading/disabled onde aplicável.

---

### T-026 — Cliente axios + interceptor de refresh + store de auth (Zustand)
- **Componente:** frontend
- **RF(s):** RF-09
- **Estimativa:** 0.5d
- **Depende de:** T-021, T-025

**Entrega:** `src/api/client.ts` com axios + interceptor que renova o access token via `/auth/refresh` em 401. `src/stores/auth.ts` (Zustand) com `accessToken` em memória (não localStorage).

**Critérios de aceitação:**
- [ ] Interceptor de request injeta `Authorization: Bearer <token>` quando há token.
- [ ] Interceptor de response em 401 chama `/auth/refresh`, atualiza o token, e retenta a request original (1x).
- [ ] Falha em `/auth/refresh` desloga (limpa store + redireciona para `/login`).
- [ ] Cookies enviados com `withCredentials: true`.

---

### T-027 — Página /login (telefone + OTP em 2 steps)
- **Componente:** frontend
- **RF(s):** RF-09
- **Estimativa:** 1d
- **Depende de:** T-026

**Entrega:** página `/login` com 2 etapas no mesmo componente: input de telefone → submit chama `POST /auth/otp/request` → mostra input de 6 dígitos → submit chama `POST /auth/otp/verify` → redireciona para `/`.

**Critérios de aceitação:**
- [ ] Validação de telefone em pt-BR (mín 10 dígitos, máx 13).
- [ ] Após enviar telefone, mostra mensagem "Código enviado pelo WhatsApp".
- [ ] Botão "Reenviar código" com cooldown visível de 60s.
- [ ] Erro de OTP errado exibe contador de tentativas restantes.
- [ ] Loading state em ambos os submits.

---

### T-028 — Página / (Dashboard mensal)
- **Componente:** frontend
- **RF(s):** RF-12
- **Estimativa:** 1.5d
- **Depende de:** T-024, T-026

**Entrega:** dashboard mobile-first com: total gasto no mês corrente, top 5 categorias com valor e %, gráfico (Recharts) de pizza, últimos 10 lançamentos.

**Critérios de aceitação:**
- [ ] Total exibido em destaque no topo (`R$ X.XXX,YY`).
- [ ] Top 5 categorias listadas com nome, valor, %, cor e ícone.
- [ ] Gráfico de pizza renderiza com as cores das categorias.
- [ ] Últimos 10 lançamentos mostram descrição, categoria, valor e data.
- [ ] Layout funciona bem em viewport 360x640 (mobile) e 1280x720 (desktop).

---

### T-029 — Página /lancamentos (lista filtrável + busca)
- **Componente:** frontend
- **RF(s):** RF-13, RF-05
- **Estimativa:** 1.5d
- **Depende de:** T-024, T-026

**Entrega:** lista paginada de lançamentos com filtros (período, categoria) e busca textual. Botão "+ Novo" abre BottomSheet com formulário de novo gasto.

**Critérios de aceitação:**
- [ ] Filtros: date range picker (de/até), select de categoria, input de busca (debounce 300ms).
- [ ] Scroll infinito ou paginação visível.
- [ ] BottomSheet de novo gasto usa React Hook Form + Zod (description, amount, category, date).
- [ ] Após criar, lista atualiza automaticamente (invalida cache do TanStack Query).
- [ ] Estado vazio amigável ("Nenhum lançamento neste filtro").

---

### T-030 — Página /lancamentos/:id (detalhe + edição + exclusão)
- **Componente:** frontend
- **RF(s):** RF-14
- **Estimativa:** 1d
- **Depende de:** T-029

**Entrega:** página de detalhe com formulário pré-preenchido para edição. Botão "Excluir" abre modal de confirmação.

**Critérios de aceitação:**
- [ ] Formulário de edição usa os mesmos componentes do "novo gasto".
- [ ] Botão "Salvar" chama `PATCH /expenses/{id}` e mostra Toast de sucesso.
- [ ] Botão "Excluir" pede confirmação ("Tem certeza?") antes de chamar `DELETE`.
- [ ] Após excluir, redireciona para `/lancamentos`.
- [ ] Exibe `raw_message` se o gasto veio do WhatsApp (`source = 'WHATSAPP'`).

---

### T-031 — Página /perfil (edição de nome + logout)
- **Componente:** frontend
- **RF(s):** RF-17, RF-11
- **Estimativa:** 0.5d
- **Depende de:** T-023, T-026

**Entrega:** página com input de nome (editável), telefone (read-only), e botão "Sair".

**Critérios de aceitação:**
- [ ] Nome editável com botão "Salvar" → chama `PATCH /me`.
- [ ] Telefone exibido formatado (`(47) 99999-9999`).
- [ ] Logout chama `DELETE /auth/sessions/current`, limpa store, redireciona para `/login`.

---

### T-032 — Teste em dispositivos reais (Android + iOS)
- **Componente:** qa
- **RF(s):** RF-05, RF-09, RF-11, RF-12, RF-13, RF-14, RF-17
- **Estimativa:** 0.5d
- **Depende de:** T-027, T-028, T-029, T-030, T-031

**Entrega:** relatório em `docs/qa/release-2-mobile.md` documentando o teste do fluxo completo (login → dashboard → criar/editar/excluir → logout) em pelo menos 1 Android e 1 iOS reais.

**Critérios de aceitação:**
- [ ] Fluxo completo executado nos dois dispositivos sem bloqueios.
- [ ] Screenshots de cada tela.
- [ ] Bugs encontrados listados como tarefas adicionais (não bloqueia release se forem cosméticos).

---

# Release 3 — Categorias e Relatórios via WhatsApp

> **Objetivo:** usuário consegue gerenciar suas categorias na web e pedir resumos pelo WhatsApp (texto + gráfico).
> **Critério de pronto:** RF-06, RF-07, RF-08, RF-10, RF-15, RF-16 + gráfico via Opção A funcional.
> **Estimativa total:** ~8 dias.

---

### T-033 — Seed Flyway de categorias globais
- **Componente:** backend/db
- **RF(s):** RF-06
- **Estimativa:** 0.5d
- **Depende de:** T-005

**Entrega:** migration `V2__seed_global_categories.sql` inserindo as 12 categorias globais da [analise-tecnica.md §6.3](./analise-tecnica.md) + "Sem categoria", cada uma com ícone, cor e keywords para auxiliar matching.

**Critérios de aceitação:**
- [ ] Categorias inseridas: Alimentação, Mercado, Transporte, Lazer, Saúde, Moradia, Educação, Vestuário, Serviços, Investimento, Renda, Outros, Sem categoria.
- [ ] Cada categoria tem `icon` (lucide-react name) e `color` (hex).
- [ ] `keywords` (text[]) populado com palavras comuns (ex.: Mercado: ["mercado", "supermercado", "atacadão"]).
- [ ] Migration é idempotente em re-execução (usa `ON CONFLICT DO NOTHING`).

---

### T-034 — Endpoints CRUD /categories
- **Componente:** backend
- **RF(s):** RF-07
- **Estimativa:** 1d
- **Depende de:** T-021, T-033

**Entrega:** endpoints `GET /categories` (retorna globais ativas + customizadas do user), `POST /categories`, `PATCH /categories/{id}`, `DELETE /categories/{id}` (soft delete via `active=false`).

**Critérios de aceitação:**
- [ ] `GET` retorna lista unificada com flag `is_custom`.
- [ ] `POST` valida: name único por user (entre as ativas), name 1-50 chars, icon e color obrigatórios.
- [ ] `PATCH` só permite editar customizadas (não as globais "puras"); valida unicidade do nome.
- [ ] `DELETE` seta `active=false`; lançamentos passados preservam a referência.
- [ ] Tentativa de operar categoria de outro user retorna 404.

---

### T-035 — Cópia/override de categoria global (RF-08)
- **Componente:** backend
- **RF(s):** RF-08
- **Estimativa:** 1d
- **Depende de:** T-034

**Entrega:** endpoint dedicado `POST /categories/from-global/{globalId}` que cria uma `categories` para o user com `global_id` apontando para o original. Backend resolve a categoria do user antes da global em buscas.

**Critérios de aceitação:**
- [ ] User pode customizar uma categoria global (criando sua cópia com novas cor/ícone).
- [ ] `GET /categories` esconde a global se o user já tem a cópia (mostra só a customizada).
- [ ] Resolução de categoria por nome no `ExpenseService` prioriza a versão do user.
- [ ] Documentar a decisão tomada para lançamentos passados (re-vincular ou manter).

---

### T-036 — Página /categorias (CRUD em drawer/modal)
- **Componente:** frontend
- **RF(s):** RF-07, RF-08
- **Estimativa:** 1d
- **Depende de:** T-034, T-035

**Entrega:** página de categorias mostrando globais e customizadas com ícone e cor. Botão "+ Nova" e botão de editar em cada uma abrem BottomSheet com formulário.

**Critérios de aceitação:**
- [ ] Lista visualmente clara separando globais e customizadas.
- [ ] Form de criar/editar com input de nome, seletor de ícone (lista de lucide-react), seletor de cor (paleta predefinida).
- [ ] Categoria customizada pode ser excluída com modal de confirmação.
- [ ] Categoria global tem botão "Personalizar" que abre o form pré-preenchido.

---

### T-037 — Comando "site/link/web" no parser de mensagens (RF-10)
- **Componente:** backend
- **RF(s):** RF-10
- **Estimativa:** 0.5d
- **Depende de:** T-011

**Entrega:** roteador de comandos antes do parser de gasto: se a mensagem é "site", "link", "web", "acessar" (case-insensitive), responde com a URL da aplicação.

**Critérios de aceitação:**
- [ ] Lista de gatilhos configurável via property.
- [ ] URL vem de `application.yml` (`halo.web.public-url`).
- [ ] Mensagem de resposta inclui o link clicável.
- [ ] Comando é tratado antes da IA (não consome quota Gemini).

---

### T-038 — Parser de comandos "resumo" e "resumo &lt;mês&gt;" (RF-15, RF-16)
- **Componente:** backend
- **RF(s):** RF-15, RF-16
- **Estimativa:** 0.5d
- **Depende de:** T-011

**Entrega:** roteador identifica "resumo", "resumo do mês", "resumo &lt;mês&gt;", "resumo &lt;mês&gt; &lt;ano&gt;" e dispara o `ReportService`. Fallback para Gemini se a regex falhar (interpretação solta).

**Critérios de aceitação:**
- [ ] Reconhece meses em pt-BR completos e abreviados (jan, fev, ...).
- [ ] Sem mês informado → mês corrente.
- [ ] Ano opcional; default ao ano corrente.
- [ ] Sem dados no período → mensagem amigável e fluxo encerra (não tenta gráfico).

---

### T-039 — ReportService: agregação SQL + endpoints REST
- **Componente:** backend
- **RF(s):** RF-12, RF-15
- **Estimativa:** 1d
- **Depende de:** T-024

**Entrega:** `ReportService.monthly(userId, yearMonth)` retorna totais por categoria + total geral + lista de gastos. Endpoints `GET /reports/monthly?month=YYYY-MM` e `GET /reports/categories?from=&to=`.

**Critérios de aceitação:**
- [ ] Query usa `GROUP BY category, date_trunc('month', occurred_at)`.
- [ ] Resposta inclui: total, breakdown por categoria (id, name, color, total, %).
- [ ] Endpoint REST autenticado; só retorna dados do user.
- [ ] Lançamentos soft-deletados são excluídos.

---

### T-040 — Formatador de tabela em texto monoespaçado para WhatsApp
- **Componente:** backend
- **RF(s):** RF-15
- **Estimativa:** 0.5d
- **Depende de:** T-039

**Entrega:** `WhatsappTextReportFormatter` que recebe o `MonthlyReport` e produz uma string com tabela monoespaçada (entre crases triplas, que o WhatsApp renderiza como código).

**Critérios de aceitação:**
- [ ] Cabeçalho com mês/ano em pt-BR.
- [ ] Colunas: Categoria | Valor | %.
- [ ] Linha final de total.
- [ ] Valores em pt-BR (R$ 1.234,56).
- [ ] Output cabe em 1 mensagem (sem ultrapassar limites do WhatsApp).

---

### T-041 — Geração de gráfico via QuickChart (Opção A)
- **Componente:** backend
- **RF(s):** RF-15
- **Estimativa:** 1.5d
- **Depende de:** T-039

**Entrega:** `ChartGenerator.monthlyPie(report)` que monta o config JSON do Chart.js, chama `https://quickchart.io/chart`, recebe imagem PNG e retorna `byte[]`.

**Critérios de aceitação:**
- [ ] Gráfico de pizza com cores das categorias.
- [ ] Tipo de gráfico (pie/bar) escolhido conforme nº de categorias (≤ 6 = pie, > 6 = bar).
- [ ] Falha no QuickChart degrada para envio apenas da tabela em texto (sem imagem).
- [ ] Timeout de 5s na chamada.

---

### T-042 — Envio de mídia (gráfico) via Evolution sendMedia
- **Componente:** backend
- **RF(s):** RF-15
- **Estimativa:** 0.5d
- **Depende de:** T-012, T-041

**Entrega:** `EvolutionClient.sendMedia(phone, imageBytes, caption)` que faz `POST /message/sendMedia/{instance}` com a imagem em base64.

**Critérios de aceitação:**
- [ ] Mensagem enviada com a imagem + caption opcional.
- [ ] Retry e circuit breaker mesma config do `sendText`.
- [ ] Test com imagem de 50KB funciona.

---

### T-043 — Cache de classificação por descrição normalizada
- **Componente:** backend
- **RF(s):** RF-03 (otimização §9.4 do doc técnico)
- **Estimativa:** 0.5d
- **Depende de:** T-014

**Entrega:** antes de chamar Gemini, busca em cache (Caffeine, in-memory) por hash da descrição normalizada (lowercase, sem acento, sem números). TTL 30 dias.

**Critérios de aceitação:**
- [ ] Cache hit não chama Gemini (zero token).
- [ ] Cache invalidado quando categoria é renomeada/desativada.
- [ ] Limite de tamanho 10k entradas (LRU).
- [ ] Hit rate é loggado para análise.

---

# Release 4 — Hardening, Deploy de Produção e Piloto

> **Objetivo:** aplicação rodando em produção (VPS + domínio + TLS), pelo menos 3 usuários em piloto por 30 dias, métricas da §3 do PRD coletadas.
> **Critério de pronto:** §9 Release 4 do PRD.
> **Estimativa total:** ~6 dias.

---

### T-044 — Hardening do layer web (rate limit + headers + CORS)
- **Componente:** backend
- **RF(s):** RNF (PRD §7)
- **Estimativa:** 1d
- **Depende de:** T-021

**Entrega:** Bucket4j em `/auth/otp/request` (já feito) + headers de segurança globais (HSTS, CSP, X-Frame-Options, Referrer-Policy) + CORS restritivo apenas para o frontend.

**Critérios de aceitação:**
- [ ] Rate limit: 1 OTP a cada 60s por telefone, 100 reqs/min por IP nas demais rotas.
- [ ] Headers de segurança verificados via `curl -I` ou securityheaders.com.
- [ ] CORS configurado por property; nega request de origem desconhecida.
- [ ] Webhook do Evolution validado por apikey já cobre auth (T-008).

---

### T-045 — Dockerfile produção backend (multi-stage)
- **Componente:** infra
- **RF(s):** —
- **Estimativa:** 0.5d
- **Depende de:** T-002

**Entrega:** `backend/Dockerfile` com 2 stages: build (maven + jdk 21) e runtime (eclipse-temurin jre 21 alpine + fat jar).

**Critérios de aceitação:**
- [ ] Imagem final < 250 MB.
- [ ] Container responde em `/actuator/health`.
- [ ] Usuário não-root no container.
- [ ] Healthcheck no Dockerfile.

---

### T-046 — Dockerfile produção frontend (nginx + build estático)
- **Componente:** infra
- **RF(s):** —
- **Estimativa:** 0.5d
- **Depende de:** T-003

**Entrega:** `frontend/Dockerfile` multi-stage: build com node, runtime com nginx alpine servindo o `dist/`.

**Critérios de aceitação:**
- [ ] Imagem final < 50 MB.
- [ ] `nginx.conf` configura SPA fallback (todas as rotas servem `index.html`).
- [ ] Gzip habilitado.
- [ ] `VITE_API_BASE_URL` injetada no build via build arg.

---

### T-047 — docker-compose.prod.yml + Traefik + Let's Encrypt
- **Componente:** infra
- **RF(s):** —
- **Estimativa:** 1d
- **Depende de:** T-045, T-046

**Entrega:** `infra/docker-compose.prod.yml` orquestrando Traefik 3, frontend, backend, evolution, postgres, com labels para roteamento por subdomínio e TLS automático via Let's Encrypt.

**Critérios de aceitação:**
- [ ] Subdomínios: `halo.<domain>`, `api.halo.<domain>`, `evolution.<domain>`.
- [ ] Certificados Let's Encrypt emitidos automaticamente.
- [ ] HTTP redireciona para HTTPS.
- [ ] Volumes persistentes para Postgres e Evolution.
- [ ] `traefik.yml` com configuração mínima documentada.

---

### T-048 — Cron de backup diário do Postgres
- **Componente:** infra
- **RF(s):** —
- **Estimativa:** 0.5d
- **Depende de:** T-047

**Entrega:** script shell + cron job no host que executa `pg_dump` diário para `/opt/halo/backups/` com retenção de 14 dias.

**Critérios de aceitação:**
- [ ] Backup roda às 03:00 UTC.
- [ ] Arquivo nomeado `halo-YYYYMMDD.sql.gz`.
- [ ] Backups com mais de 14 dias são removidos.
- [ ] Script logado em `/var/log/halo-backup.log`.
- [ ] Documentação inclui passo de restore.

---

### T-049 — Runbook de operação
- **Componente:** docs
- **RF(s):** —
- **Estimativa:** 0.5d
- **Depende de:** T-048

**Entrega:** `docs/runbook.md` com procedimentos para: reconexão Evolution GO (QR), restore de backup Postgres, reinício de containers, troubleshooting de logs.

**Critérios de aceitação:**
- [ ] Runbook tem ao menos 4 procedimentos com comandos exatos.
- [ ] Cada procedimento tem pré-requisitos e pós-condições claras.
- [ ] Validado executando 1 dos procedimentos (restore em ambiente de teste).

---

### T-050 — Primeiro deploy em produção + DNS + smoke test
- **Componente:** infra
- **RF(s):** todos
- **Estimativa:** 1d
- **Depende de:** T-047, T-049

**Entrega:** VPS provisionada, DNS apontando, `docker-compose.prod.yml` rodando, smoke test executado (registro de gasto via WhatsApp + login na web + dashboard).

**Critérios de aceitação:**
- [ ] Acessos via HTTPS funcionais nos 3 subdomínios.
- [ ] Smoke test E2E executado pelo desenvolvedor com sucesso.
- [ ] Métricas iniciais da §3 do PRD coletadas (queries SQL salvas em `docs/qa/metrics-queries.sql`).
- [ ] Canal de feedback do piloto definido (Google Form ou similar) e link salvo em `docs/qa/pilot-feedback.md`.

---

# Resumo

| Release | Tarefas | Estimativa total |
|---|---|---|
| R0 — Setup | T-001 a T-007 (7) | ~4 dias |
| R1 — MVP WhatsApp | T-008 a T-018 (11) | ~8 dias |
| R2 — Web mínima | T-019 a T-032 (14) | ~10 dias |
| R3 — Relatórios | T-033 a T-043 (11) | ~8 dias |
| R4 — Hardening + Deploy | T-044 a T-050 (7) | ~5 dias |
| **Total** | **50** | **~35 dias** |

> A estimativa de 35 dias é maior que os 20-28 dias do doc técnico §15 porque aqui inclui QA explícito (T-018, T-032), documentação operacional (T-049) e smoke test em produção (T-050). Em paralelização (2+ devs), o caminho crítico cai significativamente — analisar com base no grafo de dependências acima.

---

> Backlog vivo. Mudanças de escopo geram novas tarefas (`T-XXX` sequencial) sem renumerar as existentes. Tarefas canceladas são marcadas com `~~T-XXX~~` em vez de removidas.

# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project context

Halo is a personal expense tracker where the **primary input channel is WhatsApp** (the user types `Mercado 87,30`, AI categorizes and stores it) and the **secondary channel is a mobile-first PWA** for review/editing. SENAI academic mini-project.

Authoritative documentation lives in `docs/`:
- `docs/prd.md` — product requirements (RF-XX references used in code/commits).
- `docs/analise-tecnica.md` — architecture decisions (ADRs, schema, flows). Cited from code comments as `§N`.
- `docs/tasks.md` — backlog with `T-XXX` task IDs. Code comments and commits reference these.
- `docs/setup-evolution.md` — how to wire Evolution GO to the backend webhook.

When code comments reference `§N` or `T-XXX`, look in those docs first — they carry the rationale for non-obvious design choices.

## Status do projeto

**Fase 0 — Setup: 100% concluída** (T-001 a T-008 mergeadas em `main`). Próxima fase é a 1 — MVP WhatsApp, começando por T-009.

| Task | Entrega | Issue |
|---|---|---|
| T-001 | Monorepo + LICENSE + README + .gitignore | #1 |
| T-002 | Scaffold backend Spring Boot 3.3 + Java 21 | #2 |
| T-003 | Scaffold frontend Vite + React + Tailwind | #3 |
| T-004 | docker-compose dev (Postgres 16 + Evolution Go) | #4 |
| T-005 | Migration Flyway V1 (9 tabelas + índices) | #5 |
| T-006 | Pareamento real do `halo-bot` no Evolution + runbook | #6 |
| T-007 | CI básico (mvn verify + npm lint/build) | #7 |
| T-008 | `POST /webhooks/evolution` + DTO + auth apikey | #8 |

## Monorepo layout

```
backend/   Spring Boot 3.3 + Java 21 API (Maven)
frontend/  React 18 + Vite + TypeScript + Tailwind PWA
infra/     docker-compose for Postgres 16 + Evolution GO
docs/      PRD, architecture, task backlog
```

Each subproject has its own `.env.example`. Copy it to `.env` before running.

> **Setup detalhado fica no `README.md` raiz**, incluindo o passo de ativação de licença do Evolution Go e gestão dos dois níveis de auth (global API key vs instance token). Este CLAUDE.md cobre só o que Claude precisa para trabalhar no código; o README cobre o que um operador humano precisa para subir o stack.

## Commands

### Local dev stack
```bash
# 1. Infra (Postgres + Evolution GO) — run from infra/
cd infra && docker compose up -d

# 2. Backend
cd backend && mvn spring-boot:run        # http://localhost:8080

# 3. Frontend
cd frontend && npm install && npm run dev   # http://localhost:5173
```

Evolution GO defaults to host port 8080, which collides with the backend. If you need both up at the same time, change `EVOLUTION_HOST_PORT` in `infra/.env` (ex.: 8088) and aponte o backend para esse novo valor via `EVOLUTION_BASE_URL`.

### Backend (run from `backend/`)
```bash
mvn -B -ntp verify       # full build + tests (what CI runs)
mvn test                 # tests only
mvn spring-boot:run      # start API
mvn -Dtest=EvolutionWebhookControllerTest test           # single test class
mvn -Dtest=EvolutionWebhookControllerTest#sem_apikey_retorna_401 test  # single method
```
Integration tests use **Testcontainers + Postgres**, so Docker must be running.

> O Maven do sistema costuma estar com Java 8/3.6.3. Use `JAVA_HOME=/usr/lib/jvm/java-21-amazon-corretto` (ou caminho equivalente) ao rodar `mvn` localmente. O CI já usa Corretto 21 via `actions/setup-java@v4`.

### Frontend (run from `frontend/`)
```bash
npm run dev      # vite dev server
npm run lint     # tsc --noEmit (no ESLint configured)
npm run build    # tsc -b && vite build (what CI runs)
npm run preview  # serve built bundle
```

### CI
`.github/workflows/ci.yml` runs on push/PR to `main`:
- backend: `mvn -B -ntp verify` (JDK 21 Corretto)
- frontend: `npm ci && npm run lint && npm run build` (Node 20)

Caches: `setup-java` cuida do `~/.m2`, `setup-node` cuida do npm via `cache-dependency-path: frontend/package-lock.json`.

> **Atenção billing/visibility**: o repo está público. Se voltar a ser privado e o billing não estiver configurado, runs ficam em `startup_failure` antes de criar check-runs. Detalhe completo no comentário final da issue #7.

## Architecture

### High level
```
WhatsApp user → Evolution GO → POST /webhooks/evolution → Spring Boot
                                                              ↓
                                              PostgreSQL (JPA + Flyway)
                                                              ↓
                                              Gemini (parse/categorize)
                                                              ↓
                                              Evolution GO → user reply

Web user → React PWA → REST/JSON + JWT → Spring Boot
```

Everything is intended to run on a single VPS via Docker Compose behind Traefik/nginx.

### Backend module layout (target — see `analise-tecnica.md §5`)
Java root package is `dev.halo`. Modules planned/in-progress:
- `auth/` — OTP via WhatsApp + JWT access (15 min) / refresh (30 days, cookie httpOnly).
- `user/`
- `expense/`
- `category/` — global seed categories + per-user customizations (RF-07/08).
- `whatsapp/` — Evolution webhook receiver, EvolutionClient, command parser. **(currently implemented up to T-008: auth + fromMe filter; persistence + parsing entram em T-009 em diante.)**
- `ai/` — Gemini client, prompts, classifier.
- `report/` — text summaries + chart images.
- `common/` — security, exceptions, audit.

`HaloApplication` uses `@ConfigurationPropertiesScan("dev.halo")`, so any `@ConfigurationProperties` record placed under `dev.halo.*` is auto-registered.

### Security model
`common/security/SecurityConfig` is stateless (no sessions), CSRF disabled (token APIs), with HTTP Basic for placeholder authenticated routes. Public routes today:
- `/actuator/health/**`, `/actuator/info`
- `/webhooks/**` — **endpoint SEM auth dedicada**. O design inicial (T-008) era header `apikey`, mas o Evolution Go self-hosted não envia auth em webhooks de saída (issue upstream #1933 closed as not-planned), e mover o segredo pro path também não emplacou. A proteção real fica a cargo de rede / reverse proxy à frente do backend (analise-tecnica.md §10.3).
- `/error` — dispatch interno do Spring (forward em 404/405). Sem liberar, um GET no webhook cai em `httpBasic` e o navegador pede senha.

### Evolution Go — modelo de auth em 2 níveis ⚠

Descoberta operacional importante (validada em T-006):

| Endpoint | Header `apikey` espera | Uso típico |
|---|---|---|
| `GET /instance/all`, `POST /instance/create`, `DELETE /instance/delete/{id}` | **`GLOBAL_API_KEY`** (= `EVOLUTION_API_KEY` no `infra/.env`) | Ops globais: listar, criar, apagar instâncias |
| `POST /instance/connect`, `GET /instance/qr`, `GET /instance/status`, `POST /send/text`, `POST /send/media`, etc. | **Instance token** (campo `data.token` retornado por `POST /instance/create`) | Ops sobre a instância já criada |

Mandar a `GLOBAL_API_KEY` em endpoints por-instância devolve `{"error":"not authorized"}`. Mandar o instance token em endpoints globais também falha. Os dois headers se chamam `apikey` — só o valor muda.

O webhook que **recebe** eventos do Evolution Go (`POST /webhooks/evolution` no backend Halo) **não tem auth dedicada** — o Evolution Go self-hosted não envia auth em webhooks de saída (issue upstream #1933 closed as not-planned). Proteção é por rede / reverse proxy à frente do backend.

O **instance token** entrou em T-012 como `EVOLUTION_INSTANCE_TOKEN` — é o que o backend usa pra ENVIAR mensagens (`/send/text`).

### Persistence
- **Flyway** migrations in `backend/src/main/resources/db/migration/` (currently `V1__init.sql`). `spring.jpa.hibernate.ddl-auto=validate` — the schema is owned by Flyway, JPA only checks it matches.
- **All timestamps are `TIMESTAMPTZ` stored in UTC** (`hibernate.jdbc.time_zone=UTC`).
- **PKs are UUID v7** (ordered, generated by the app; `gen_random_uuid()` is the dev fallback).
- **Enums are `VARCHAR + CHECK`**, mapped with `@Enumerated(EnumType.STRING)` — easier to evolve than native PG enums.
- **`expenses` uses soft delete** via nullable `deleted_at`; partial indexes (`WHERE deleted_at IS NULL`) keep dashboard queries lean.
- **Idempotency**: `whatsapp_messages.evolution_msg_id` is UNIQUE — Evolution redeliveries must not duplicate expenses.
- **Volume `halo-pgdata`** persiste *três* bancos: `halo` (app), `evogo_auth` (Evolution Go — sessão WhatsApp/licença ativada), `evogo_users`. Apagar o volume (`docker volume rm halo-pgdata`) **desfaz a ativação de licença e o pareamento do bot** — precisa refazer o Manager UI + QR code.

### Frontend
- Routing: `react-router-dom` v6. App shell is `src/App.tsx`.
- State/data: TanStack Query for server cache, Zustand for auth.
- Forms: React Hook Form + Zod.
- Charts: Recharts (web); WhatsApp chart images are generated server-side.
- PWA: `vite-plugin-pwa` (autoUpdate, basic manifest).

## Conventions

- **Language**: code, identifiers, and config are English. Documentation, commit messages, code comments, and user-facing strings are **Portuguese (pt-BR)** — match the surrounding code, don't translate it.
- **Money**: always `numeric(12,2)` / `BigDecimal`. Never `float`/`double`.
- **DTOs**: Java records. Jackson tolerates unknown fields (`FAIL_ON_UNKNOWN_PROPERTIES=false`) so external payload evolution doesn't break deserialization — see `EvolutionPayloadDto`.
- **Webhook handlers**: validate the `apikey` header in constant time, skip `fromMe=true`, always return 200 on accepted events so Evolution doesn't retry needlessly.
- **Task references**: when a class/comment marks itself as "T-XXX scope only", honor that boundary — follow-up logic belongs in the task it was deferred to (see `docs/tasks.md`).
- **Logs**: nunca incluir conteúdo de mensagem (`message.conversation`) nem valores monetários identificáveis nos logs. Apenas metadados (event, instance, msgId, pushName). Consideração de privacidade do PRD §3.2 + analise-tecnica §10.

## Workflow para itens do kanban "Projeto Halo"

Sempre que for implementar um card do GitHub Projects "Projeto Halo" (T-XXX), siga o fluxo abaixo. Não pule etapas.

### 1. Nunca commitar direto na `main`
Crie uma branch nomeada pelo tipo de trabalho + ID da task:
- `feature/T-XXX` — entrega de funcionalidade nova.
- `bugfix/T-XXX` — correção de bug.
- `docs/<descritivo-kebab>` — para atualizações de documentação sem relação direta com uma task.

```bash
git checkout main && git pull
git checkout -b feature/T-XXX
```

### 2. Implementar respeitando o escopo da task
- Leia o card em `docs/tasks.md` (`T-XXX — …`).
- Faça apenas o que está no escopo daquela task. Lógica adjacente que aparece nos critérios de outra task (`T-YYY`) **não entra agora** — referencie nela como deferida.

### 3. Marcar todos os "Critérios de aceitação"
Antes de abrir o PR, todos os checkboxes da seção **"Critérios de aceitação"** do card devem estar verificados (`- [x]`) — tanto em `docs/tasks.md` quanto na descrição do card no GitHub Projects. Se algum critério não foi atendido, a task não está pronta.

### 4. Adicionar um comentário-cabeçalho marcando o escopo da task
Em pelo menos um arquivo central tocado pela task (controller, service ou classe equivalente), adicione um comentário Javadoc seguindo o **template estabelecido em `backend/src/main/java/dev/halo/whatsapp/EvolutionWebhookController.java` (T-008)**:

```java
/**
 * <Descrição curta do que esta classe faz> (RF-XX, analise-tecnica.md §N).
 *
 * Esta task (T-XXX) só faz: <lista enxuta do que foi entregue>.
 * <O que ficou deferido> entra em T-YYY / T-ZZZ.
 */
```

Regras do template:
- Linha 1: o que a classe faz, com referência a `RF-XX` (PRD §6) e/ou `analise-tecnica.md §N` quando aplicável.
- Linha "Esta task (T-XXX) só faz:" — escopo desta entrega (não o que a classe vai fazer no futuro).
- Linha de deferimento — quais tasks futuras vão completar o quebra-cabeça. Omita se não houver nada deferido.

### 5. Commit, push e Pull Request
Ao final:

```bash
git add <arquivos>
git commit -m "T-XXX: <resumo em pt-BR do que foi entregue>"
git push -u origin feature/T-XXX
gh pr create --base main --title "T-XXX: <título>" --body "<descrição + Closes #N>"
```

- Commits sempre referenciam o ID da task no início da mensagem (padrão dos commits existentes: `T-007`, `T-008`).
- O PR aponta para `main`.
- Coloque `Closes #N` no **body do PR**, não na mensagem de commit (assim a issue só fecha quando o PR for mergeado).

### 6. Mover o card para "In review"
Depois que o PR estiver aberto, mover o card `T-XXX` no kanban do GitHub Projects "Projeto Halo" para a coluna **"In review"**. O card só sai dessa coluna quando o PR for mergeado.

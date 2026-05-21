# Halo

> Controle de gastos pessoais via **WhatsApp** (entrada primária) + **web mobile-first** (consulta/edição). O usuário escreve `Mercado 87,30` no WhatsApp, a IA categoriza e arquiva; o dashboard web mostra o histórico. Mini-projeto acadêmico SENAI.

---

## Documentação

| Doc | Para quê |
|---|---|
| [`docs/prd.md`](docs/prd.md) | **O quê** vai ser feito e **por quê** — RFs, persona, métricas |
| [`docs/analise-tecnica.md`](docs/analise-tecnica.md) | **Como** vai ser feito — ADRs, schema, fluxos, infraestrutura |
| [`docs/tasks.md`](docs/tasks.md) | Backlog `T-XXX` ↔ GitHub Issues |
| [`docs/setup-evolution.md`](docs/setup-evolution.md) | Runbook do Evolution Go (ativar licença, criar instância, parear) |
| [`CLAUDE.md`](CLAUDE.md) | Guia para o Claude Code trabalhar no repo |

---

## Stack

| Camada | Tecnologia |
|---|---|
| Backend | Java 21 (Corretto) · Spring Boot 3.3.5 · Maven · Hibernate · Flyway |
| Frontend | React 18 · TypeScript 5.6 · Vite 5 · TailwindCSS 3 · TanStack Query · React Router 6 · Zustand · vite-plugin-pwa |
| Banco | PostgreSQL 16 |
| Bot WhatsApp | [Evolution Go](https://github.com/evolution-foundation/evolution-go) (`evoapicloud/evolution-go:latest`) |
| IA | Gemini (`gemini-2.5-flash`) — chegará a partir de T-013 |
| CI | GitHub Actions (`mvn verify` + `npm lint/build`) |

---

## Pré-requisitos

| Ferramenta | Versão recomendada | Notas |
|---|---|---|
| **Docker** + **Docker Compose v2** | 24+ | Para subir Postgres e Evolution Go |
| **Java 21** (Corretto sugerido) | 21.x | Setado em `JAVA_HOME` antes de rodar `mvn` |
| **Maven** | 3.8+ | O Maven 3.6.3 do Ubuntu funciona mas é antigo |
| **Node.js** | 20.x | Frontend e CI usam Node 20 |
| **npm** | 10.x | Vem com Node 20 |
| **gh** (GitHub CLI) | Opcional | Para abrir PRs/issues |
| **Número WhatsApp descartável** | — | Necessário no setup do bot (T-006) |

> Em distros Linux com múltiplos JDKs: `update-alternatives --list java` mostra os caminhos. No ambiente do mantenedor, Java 21 fica em `/usr/lib/jvm/java-21-amazon-corretto`. Defina `JAVA_HOME` antes de cada sessão Maven (ou exporte no `~/.bashrc`).

---

## Setup detalhado

A primeira subida envolve 7 passos (≈ 15 min). Subidas seguintes são `docker compose up -d` + start do backend/frontend.

### 1) Clonar e ler

```bash
git clone git@github.com:ldebatin/sctec-miniprojeto-halo.git
cd sctec-miniprojeto-halo
```

Estrutura:
```
backend/   API Spring Boot (porta 8080)
frontend/  PWA Vite        (porta 5173)
infra/     docker-compose: Postgres (5432) + Evolution Go (8080)
docs/      PRD, análise técnica, backlog, runbooks
```

### 2) Configurar arquivos `.env`

Existem **três** locais com `.env.example` — copie cada um para `.env` e ajuste:

#### `infra/.env`
```bash
cp infra/.env.example infra/.env
```

Edite:
```dotenv
# Postgres — dev local pode manter halo/halo
POSTGRES_USER=halo
POSTGRES_PASSWORD=halo
POSTGRES_DB=halo
POSTGRES_HOST_PORT=5432

# Timezone do compose
TZ=America/Sao_Paulo

# Evolution Go
EVOLUTION_CLIENT_NAME=halo

# GLOBAL_API_KEY do Evolution. GERE COM:
#   openssl rand -hex 16
# Token usado no header `apikey` para ops globais do Evolution (criar/listar/apagar
# instâncias). NÃO tem relação com o webhook que CHEGA no backend — esse não tem
# auth dedicada (Evolution Go não envia headers em webhooks de saída).
EVOLUTION_API_KEY=cole-aqui-o-resultado-do-openssl

# Porta exposta no host. Default 8080 colide com o backend Spring Boot.
# Se for rodar os dois ao mesmo tempo, mude para 8088 e ajuste o backend.
EVOLUTION_HOST_PORT=8080

# URL pública do backend para receber webhooks (deixe vazio em dev sem túnel).
EVOLUTION_WEBHOOK_URL=
```

> **Importante:** o compose recusa subir sem `EVOLUTION_API_KEY` definido (`${EVOLUTION_API_KEY:?...}`).

#### `backend/.env.example`
Esse arquivo lista as vars que o backend espera; o backend lê do **ambiente do processo**, não do `.env`. Use `direnv`, `dotenv-cli` ou `export` direto antes de rodar `mvn spring-boot:run`. Vars críticas:

```dotenv
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/halo
SPRING_DATASOURCE_USERNAME=halo
SPRING_DATASOURCE_PASSWORD=halo

# DEVE SER O MESMO valor de EVOLUTION_API_KEY do infra/.env. Lido pelo backend
# como halo.evolution.api-key e usado nas chamadas de SAÍDA aos endpoints
# globais do Evolution (/instance/all etc.). O webhook que CHEGA no backend
# não usa auth dedicada.
EVOLUTION_API_KEY=cole-aqui-o-mesmo-valor-do-infra
```

#### `frontend/.env.example`
```bash
cp frontend/.env.example frontend/.env
```

```dotenv
VITE_API_BASE_URL=http://localhost:8080
```

### 3) Subir Postgres + Evolution Go

```bash
cd infra
docker compose up -d
docker compose ps
```

Espere até ambos ficarem **`(healthy)`** (postgres em ~3 s, evolution-go em ~17 s).

Healthchecks:
- `pg_isready` para o postgres.
- `wget --spider /swagger/index.html` para o evolution-go.

Volumes:
- **`halo-pgdata`** (nomeado): persiste os bancos `halo`, `evogo_auth`, `evogo_users` entre restarts. `docker compose down` preserva. **`docker volume rm halo-pgdata` apaga tudo — incluindo licença do Evolution e pareamento do bot.**

Bancos criados automaticamente pelo init script `infra/postgres/init/01-create-evolution-dbs.sql`:
- `halo` — aplicação principal (Flyway aplica `V1__init.sql` no primeiro start do backend).
- `evogo_auth` + `evogo_users` — exigidos pela arquitetura dual-DB do Evolution Go.

### 4) Ativar licença do Evolution Go (uma vez por volume)

⚠️ **Sem este passo, todos os endpoints `/instance/*` respondem HTTP 503** — Evolution Go exige ativação inicial.

1. Abra **<http://localhost:8080/manager/login>** no navegador.
2. **API URL**: `http://localhost:8080`
3. **API Key**: cole o valor de `EVOLUTION_API_KEY` do `infra/.env`.
4. Siga o fluxo de cadastro/ativação Evolution.

Validar que destravou:
```bash
API_KEY=$(grep EVOLUTION_API_KEY infra/.env | cut -d= -f2)
curl -sS -o /dev/null -w "%{http_code}\n" -H "apikey: $API_KEY" \
  http://localhost:8080/instance/all
# Antes da ativação: 503
# Depois da ativação: 200
```

A ativação fica **persistida em `evogo_auth`** dentro do volume `halo-pgdata`. Subidas seguintes não precisam refazer.

### 5) Criar a instância `halo-bot` e parear o WhatsApp

> Existe um runbook completo em [`docs/setup-evolution.md`](docs/setup-evolution.md). Versão resumida aqui.

#### 5.1 Criar a instância

```bash
API_KEY=$(grep EVOLUTION_API_KEY infra/.env | cut -d= -f2)
INSTANCE_TOKEN=$(uuidgen)
echo "INSTANCE_TOKEN=$INSTANCE_TOKEN  # ANOTE ISSO — vai precisar para enviar mensagens"

curl -sS -X POST -H "apikey: $API_KEY" -H "Content-Type: application/json" \
  http://localhost:8080/instance/create \
  -d "{\"name\":\"halo-bot\",\"token\":\"$INSTANCE_TOKEN\"}"
```

`POST /instance/create` **exige** o campo `token` no body (Evolution Go não autogera). Use `uuidgen` para um UUID descartável.

#### 5.2 Disparar conexão e baixar o QR

```bash
# Disparar
curl -sS -X POST -H "apikey: $INSTANCE_TOKEN" -H "instance: halo-bot" \
  -H "Content-Type: application/json" \
  http://localhost:8080/instance/connect -d '{}'

# Baixar QR
curl -sS -H "apikey: $INSTANCE_TOKEN" -H "instance: halo-bot" \
  http://localhost:8080/instance/qr | \
  python3 -c "import sys,json; d=json.load(sys.stdin); print(d['data']['Qrcode'].split(',',1)[1])" | \
  base64 -d > /tmp/halo-qr.png

xdg-open /tmp/halo-qr.png   # ou abra manualmente
```

> Note os **dois `apikey` diferentes**: o `API_KEY` global é para `instance/create`; o `INSTANCE_TOKEN` é para tudo que envolve a instância já criada (connect, qr, status, send/*). Detalhes na seção [Tokens e secrets](#tokens-e-secrets).

#### 5.3 Escanear no celular descartável

WhatsApp → ⋮ → **Aparelhos conectados** → **Conectar um aparelho** → escanear o QR.

QRs expiram em ~30 s — se demorar, repita o `GET /instance/qr`.

#### 5.4 Confirmar conexão

```bash
curl -sS -H "apikey: $INSTANCE_TOKEN" -H "instance: halo-bot" \
  http://localhost:8080/instance/status
# Esperado: {"data":{"Connected":true,"LoggedIn":true,"Name":"<seu-nome>"},...}
```

#### 5.5 (Opcional) Enviar mensagem de teste

```bash
curl -sS -X POST -H "apikey: $INSTANCE_TOKEN" -H "instance: halo-bot" \
  -H "Content-Type: application/json" \
  http://localhost:8080/send/text \
  -d '{"number":"55XX9XXXXXXXX","text":"Halo bot online 👋"}'
```

### 6) Rodar o backend

```bash
cd backend

# Java 21 + Maven 3.8+
export JAVA_HOME=/usr/lib/jvm/java-21-amazon-corretto
export PATH=$JAVA_HOME/bin:$PATH

# Vars do banco + segredos do Evolution (mesmos valores do infra/.env)
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/halo
export SPRING_DATASOURCE_USERNAME=halo
export SPRING_DATASOURCE_PASSWORD=halo
export EVOLUTION_API_KEY=<o-mesmo-do-infra/.env>

mvn spring-boot:run
```

Sucesso quando ver:
```
Tomcat started on port 8080
Started HaloApplication in X.XXX seconds
```

Validar:
```bash
curl -s http://localhost:8080/actuator/health
# {"status":"UP","groups":["liveness","readiness"]}
```

> Se você está rodando o Evolution Go também na 8080, **derrube o evolution-go antes** (`docker compose stop evolution-go`) ou mude `EVOLUTION_HOST_PORT` no `infra/.env` para 8088.

#### Testando o webhook localmente

```bash
curl -sS -X POST -H "Content-Type: application/json" \
  http://localhost:8080/webhooks/evolution \
  -d '{
        "event":"messages.upsert",
        "instance":"halo-bot",
        "data":{
          "key":{"id":"TEST-1","remoteJid":"5547999999999@s.whatsapp.net","fromMe":false},
          "message":{"conversation":"Mercado 87,30"},
          "messageTimestamp":1716042000,
          "pushName":"Teste"
        }
      }'
# Esperado: 200 OK
```

O endpoint **não tem auth dedicada** — o Evolution Go self-hosted não envia headers de auth em webhooks de saída (issue upstream #1933 closed as not-planned). Proteção é por rede / reverse proxy à frente do backend.

### 7) Rodar o frontend

```bash
cd frontend
npm install
npm run dev
# http://localhost:5173 — mostra "Halo" centralizado em azul
```

---

## Tokens e secrets

O Evolution Go tem **dois níveis de autenticação**, ambos no header `apikey` — só o valor muda:

| Endpoint | `apikey` esperado | De onde vem |
|---|---|---|
| `POST /instance/create` | `GLOBAL_API_KEY` | `EVOLUTION_API_KEY` do `infra/.env` |
| `GET /instance/all`, `DELETE /instance/delete/{id}` | `GLOBAL_API_KEY` | Mesma de cima |
| `POST /instance/connect`, `GET /instance/qr`, `GET /instance/status` | **Instance token** | Campo `data.token` retornado por `POST /instance/create` |
| `POST /send/text`, `POST /send/media`, `POST /message/edit` etc. | **Instance token** | Mesma de cima |

Mandar o token errado em qualquer um devolve `{"error":"not authorized"}`.

Para os webhooks que o Evolution **envia** ao backend Halo, **não há auth dedicada** — o Evolution Go self-hosted não envia auth em webhooks de saída (issue upstream #1933 closed as not-planned). O endpoint `POST /webhooks/evolution` fica aberto; proteção é por rede / reverse proxy à frente do backend.

### Onde cada token entra

| Token | Lido por | Variável |
|---|---|---|
| `GLOBAL_API_KEY` | Compose do Evolution Go | `EVOLUTION_API_KEY` em `infra/.env` |
| `GLOBAL_API_KEY` (mesmo valor) | Backend Halo — chama endpoints globais | `EVOLUTION_API_KEY` em `backend/.env` (lida como `halo.evolution.api-key`) |
| `INSTANCE_TOKEN` (gerado no `instance/create`) | Backend Halo — enviar mensagens | `EVOLUTION_INSTANCE_TOKEN` em `backend/.env` |

**Boas práticas:**
- Gere a `EVOLUTION_API_KEY` com `openssl rand -hex 16` — qualquer valor longo serve.
- O `INSTANCE_TOKEN` é melhor ser um UUID (`uuidgen`) — o Evolution Go não impõe formato.
- **Anote o INSTANCE_TOKEN em algum lugar seguro fora do repo** — ele só é mostrado uma vez na resposta de `POST /instance/create`. Se perder, dá pra deletar a instância e recriar (precisa parear de novo).
- Em produção (futuro): usar um secret manager (Vault, Doppler, AWS SM) em vez de `.env`.

---

## Operação do dia-a-dia

```bash
cd infra

# Subir
docker compose up -d
docker compose ps

# Derrubar (preserva volume = preserva licença + pareamento)
docker compose down

# Reset completo (apaga banco, licença e pareamento)
docker compose down -v
docker volume rm halo-pgdata  # se sobrar

# Logs
docker compose logs -f postgres
docker compose logs -f evolution-go

# Restart pontual de um serviço
docker compose restart evolution-go
```

Operações comuns no Evolution Go (cole `API_KEY` e `INSTANCE_TOKEN` antes):

| Ação | Comando |
|---|---|
| Listar instâncias | `curl -H "apikey: $API_KEY" localhost:8080/instance/all` |
| Status de uma instância | `curl -H "apikey: $INSTANCE_TOKEN" -H "instance: halo-bot" localhost:8080/instance/status` |
| Reconectar (sem novo QR) | `curl -X POST -H "apikey: $INSTANCE_TOKEN" -H "instance: halo-bot" localhost:8080/instance/reconnect` |
| Forçar novo QR | `curl -X POST -H "apikey: $INSTANCE_TOKEN" -H "instance: halo-bot" localhost:8080/instance/forcereconnect/halo-bot` |
| Logout (mantém instância) | `curl -X DELETE -H "apikey: $INSTANCE_TOKEN" -H "instance: halo-bot" localhost:8080/instance/logout` |
| Apagar instância | `curl -X DELETE -H "apikey: $API_KEY" localhost:8080/instance/delete/<instance-id>` |

---

## Troubleshooting

| Sintoma | Causa provável | Ação |
|---|---|---|
| `docker compose up` aborta com "EVOLUTION_API_KEY required" | `.env` não copiado ou var vazia | `cp infra/.env.example infra/.env` e preencha |
| `/instance/all` retorna **503** | Licença do Evolution não ativada | Abrir `localhost:8080/manager/login` (§4 acima) |
| `/instance/create` retorna **400 token is required** | Faltou o campo `token` no body | Adicionar `"token":"<uuid>"` (`uuidgen`) |
| `/instance/connect` retorna **401 not authorized** | Mandou `GLOBAL_API_KEY` num endpoint por-instância | Trocar para o `INSTANCE_TOKEN` (campo `data.token` do create) |
| Backend não sobe — `Connection refused: localhost:5432` | Postgres não está up | `cd infra && docker compose up -d postgres` |
| Backend sobe mas `/actuator/health` retorna **401** | Spring Security exigindo basic auth — provavelmente removeram a regra `permitAll` de `/actuator/health` | Conferir `common/security/SecurityConfig.java` |
| `mvn spring-boot:run` reclama de Java 8 | `JAVA_HOME` aponta para JDK 8 | `export JAVA_HOME=/usr/lib/jvm/java-21-amazon-corretto` |
| `npm run build` falha com `must have setting "composite": true` | Regressão da T-007 — `tsconfig.node.json` perdeu `composite:true` | Restaurar de `git log -p frontend/tsconfig.node.json` |
| Workflow do GH fica em `startup_failure` em 4 s | Account-level billing lock (mesmo em repo público) | github.com/settings/billing |
| QR code expira antes de escanear | TTL ~30 s | Repetir `GET /instance/qr` |
| Mensagem volta como falha de envio | Destinatário precisa ter conversado antes (limitação WhatsApp não-business) | Mande primeiro pelo celular pareado para o destinatário |
| Quero reset completo do bot | Sessão WhatsApp travada | `docker compose down -v && docker volume rm halo-pgdata` (refaz §3 a §5) |

---

## Status do projeto

**Fase 0 — Setup: ✅ 100% concluída.** Todas as 8 tasks da fase de setup (T-001 a T-008) estão mergeadas em `main`. Detalhes em [`CLAUDE.md` § "Status do projeto"](CLAUDE.md).

**Próxima fase: Fase 1 — MVP WhatsApp**, começando em T-009 (persistência idempotente de `whatsapp_messages`). Veja [`docs/tasks.md`](docs/tasks.md) e o [GitHub Project "Projeto Halo"](https://github.com/users/ldebatin/projects/) para o backlog completo.

# Runbook — operação do Halo

Procedimentos de operação para o **stack de produção** do Halo (VPS única rodando `docker compose -f docker-compose.prod.yml`). Quando o procedimento for distinto entre **dev** (laptop) e **prod** (VPS), está sinalizado.

> Antes de qualquer procedimento destrutivo (restore, restart, reset de volume), **valide o estado atual** com a §1 (healthcheck). Use o que conhecer.

| Procedimento | §  | Pré-requisito | Quando usar |
|---|---|---|---|
| Healthcheck rápido | §1 | Acesso SSH (prod) ou shell local (dev) | Sempre antes/depois dos outros procedimentos |
| Reconectar o bot WhatsApp (QR) | §2 | Manager UI acessível | "Nada chega no WhatsApp" / `instance/status != connected` |
| Backup manual do Postgres | §3 | Volume `halo-pgdata` ativo | Antes de migrations grandes, antes de restores, ad-hoc |
| Restore de backup | §4 | Arquivo `halo-YYYYMMDD.sql.gz` à mão | Recuperação após perda de dados ou volume |
| Reinício controlado dos containers | §5 | Acesso SSH | Atualização de imagem, mudança de `.env`, suspeita de leak |
| Troubleshooting de logs | §6 | Acesso SSH | Erros em produção / investigação |

Convenções deste runbook:
- Comandos rodam **a partir do diretório `infra/`** salvo nota em contrário.
- Em prod, o arquivo do compose é `docker-compose.prod.yml`; em dev é `docker-compose.yml`. Substitua o `-f` conforme o ambiente.
- `$STAMP` quando aparece significa o timestamp no formato `YYYYMMDD-HHMMSS` (`date -u +%Y%m%d-%H%M%S`).

---

## §1 Healthcheck rápido

**Quando usar:** sempre — antes de mexer e depois de mexer.

**Pré-requisitos:**
- Estar no host (ou SSH em prod).
- `infra/.env` populado com `EVOLUTION_API_KEY` e `EVOLUTION_INSTANCE_TOKEN`.

**Comandos:**
```bash
# 1. Containers UP e healthy
docker compose ps
# todas as linhas devem ter "Up" e "(healthy)" onde houver healthcheck

# 2. Postgres aceita conexão
docker exec halo-postgres pg_isready -U halo -d halo
# espera-se: "/var/run/postgresql:5432 - accepting connections"

# 3. Backend respondendo o healthcheck do Spring
curl -sS http://localhost:8080/actuator/health
# espera-se: {"status":"UP"}

# 4. Evolution Go up e instância halo-bot conectada
curl -sS -H "apikey: $EVOLUTION_API_KEY" \
  http://localhost:8081/instance/all | jq '.[] | {name, connectionStatus}'
# espera-se: name=halo-bot, connectionStatus=open (ou "connected")

# 5. Frontend (em prod servindo via nginx ou Traefik)
curl -sS -I http://localhost:5173 | head -1
# espera-se: HTTP/1.x 200 (ou 301/302 atrás de Traefik)
```

**Pós-condição:** todos os 5 checks verdes. Se alguma falha:
- `pg_isready` falhou → §5.A (restart postgres).
- `/actuator/health` ≠ UP → §6 (logs do backend) e potencialmente §5.B.
- `connectionStatus` ≠ open → §2 (reconectar QR).

---

## §2 Reconectar o bot WhatsApp (QR)

**Quando usar:** o bot caiu (instance != `open`), apareceu erro `not authorized`, mudou de servidor (volume preservado mas Evolution Go reiniciou), ou foi feito logout pelo WhatsApp do celular pareado.

> O fluxo completo de criação da instância pela primeira vez está em [setup-evolution.md](./setup-evolution.md). Este procedimento é o **delta** para reconectar uma instância já existente.

**Pré-requisitos:**
- Container `halo-evolution-go` está UP (`docker compose ps`).
- Acesso ao **celular descartável** que está pareado (ou que vai parear).
- `EVOLUTION_API_KEY` carregada como `$API_KEY` no shell:
  ```bash
  export API_KEY=$(grep ^EVOLUTION_API_KEY infra/.env | cut -d= -f2)
  ```

**Comandos:**
```bash
# 1. Confirmar que a instância existe mas está disconnected
curl -sS -H "apikey: $API_KEY" http://localhost:8081/instance/all \
  | jq '.[] | select(.name=="halo-bot")'

# 2A. Pelo Manager UI (mais fácil):
#     - Abra http://localhost:8081/manager/login (ou domínio em prod)
#     - Cole o EVOLUTION_API_KEY no login.
#     - Clique na instância "halo-bot" → tab "Connect" → escaneie o QR.

# 2B. Pela API (sem UI):
curl -sS -X POST http://localhost:8081/instance/connect \
  -H "Content-Type: application/json" \
  -H "apikey: $API_KEY" \
  -H "instance: halo-bot" \
  -d '{}'

# 3. Obter o QR e renderizar (base64 PNG)
curl -sS -H "apikey: $API_KEY" -H "instance: halo-bot" \
  http://localhost:8081/instance/qr \
  | jq -r '.qrcode.base64' \
  | sed 's|^data:image/png;base64,||' \
  | base64 -d > /tmp/halo-qr.png
# abrir /tmp/halo-qr.png e escanear no WhatsApp do celular pareado:
# Configurações → Aparelhos conectados → Conectar um aparelho.

# 4. Aguardar 5–15 s e confirmar
curl -sS -H "apikey: $API_KEY" -H "instance: halo-bot" \
  http://localhost:8081/instance/status
# espera-se: "open" / "connected"
```

**Pós-condição:**
- `connectionStatus = open`.
- Envie uma mensagem de WhatsApp para o bot a partir de outro número e confirme em `docker logs halo-backend -f` que aparece `Mensagem registrada msgId=...`.

**Erros comuns:**
- *"QR expirou"* → repita o passo 3; o QR muda a cada ~30 s.
- *"Instance not found"* → o volume `halo-pgdata` foi resetado e a instância sumiu — siga [setup-evolution.md](./setup-evolution.md) §2 para recriar.
- *"not authorized"* em `/send/text` → o `EVOLUTION_INSTANCE_TOKEN` está fora de sincronia; obtenha o atual com:
  ```bash
  curl -sS -H "apikey: $API_KEY" http://localhost:8081/instance/all \
    | jq -r '.[] | select(.name=="halo-bot").token'
  ```
  Atualize `backend/.env` (`EVOLUTION_INSTANCE_TOKEN=...`) e §5.B (restart backend).

---

## §3 Backup manual do Postgres

**Quando usar:** antes de uma migration grande, antes de §4 (restore), ou quando o backup automático (cron, T-048) falhou e precisa-se garantir um snapshot.

**Pré-requisitos:**
- `halo-postgres` UP e healthy (§1).
- Pasta `/opt/halo/backups/` existe e tem espaço (~50–200 MB livres). Em dev, `./backups/` é fine.

**Comandos:**
```bash
STAMP=$(date -u +%Y%m%d-%H%M%S)
BACKUP_DIR=/opt/halo/backups        # em dev: ./backups
mkdir -p "$BACKUP_DIR"

# pg_dump do banco do app (halo). Para também pegar evogo_auth e evogo_users:
#   --dbname=halo  →  trocar para  pg_dumpall (vai capturar TODOS os bancos)
# Mas pg_dumpall não comprime nativamente — preferimos um pg_dump por banco.
docker exec halo-postgres pg_dump \
    -U halo -d halo \
    --format=plain --no-owner --no-privileges \
  | gzip -9 > "$BACKUP_DIR/halo-$STAMP.sql.gz"

ls -lh "$BACKUP_DIR/halo-$STAMP.sql.gz"
```

**Pós-condição:**
- Arquivo `halo-YYYYMMDD-HHMMSS.sql.gz` no diretório, tamanho > 1 KB.
- Validar integridade rápida:
  ```bash
  gunzip -t "$BACKUP_DIR/halo-$STAMP.sql.gz" && echo "OK"
  # se nada imprimiu além de "OK", o gzip está íntegro.
  ```

**Notas:**
- Para incluir as duas bases do Evolution Go (que vivem no mesmo Postgres — ver `infra/postgres/init/01-create-evolution-dbs.sql`), faça um dump por base:
  ```bash
  for db in halo evogo_auth evogo_users; do
    docker exec halo-postgres pg_dump -U halo -d "$db" --format=plain \
      --no-owner --no-privileges \
      | gzip -9 > "$BACKUP_DIR/$db-$STAMP.sql.gz"
  done
  ```
- O backup do Evolution preserva o pareamento do bot (sessão WhatsApp). Sem esse dump, restaurar só o `halo` exige refazer §2 (QR).

---

## §4 Restore de backup do Postgres

**Quando usar:** rollback de uma migration ruim, recuperar de corrupção, restaurar em um novo host após perda do volume `halo-pgdata`.

> ⚠️ **Destrutivo.** O restore aqui drop-and-recreate da base `halo`. **Faça §3 antes** para ter um snapshot do estado atual, mesmo que ruim — pode salvar pele.

**Pré-requisitos:**
- Backup íntegro (`gunzip -t arquivo.sql.gz` retorna sem erro).
- Postgres UP. Backend e Evolution Go **parados** durante o restore (evita conexões competindo pelo DROP):
  ```bash
  docker compose stop backend evolution-go
  ```

**Comandos:**
```bash
BACKUP_FILE=/opt/halo/backups/halo-YYYYMMDD-HHMMSS.sql.gz   # ajuste

# 1. Snapshot defensivo do estado atual (não pula esta linha)
docker exec halo-postgres pg_dump -U halo -d halo --format=plain --no-owner --no-privileges \
  | gzip -9 > "/opt/halo/backups/halo-pre-restore-$(date -u +%Y%m%d-%H%M%S).sql.gz"

# 2. Drop e recreate da base (sem dropar usuário ou as outras bases)
docker exec halo-postgres psql -U halo -d postgres -c "DROP DATABASE IF EXISTS halo;"
docker exec halo-postgres psql -U halo -d postgres -c "CREATE DATABASE halo OWNER halo;"

# 3. Restore a partir do .sql.gz
gunzip -c "$BACKUP_FILE" | docker exec -i halo-postgres psql -U halo -d halo

# 4. Subir o backend e validar
docker compose start backend
curl -sS http://localhost:8080/actuator/health
```

**Pós-condição:**
- `/actuator/health` retorna `UP`.
- Flyway log no startup do backend não reporta migrations pendentes:
  ```bash
  docker logs halo-backend 2>&1 | grep -i "flyway"
  # espera-se: "Schema 'public' is up to date" ou aplicação de migrations já presentes
  ```
- Smoke: `curl -sS http://localhost:8080/categories -H "Authorization: Bearer <jwt>"` devolve a lista esperada do snapshot.

**Notas:**
- Se o backup é de uma versão **mais antiga** do schema, ao subir o backend o Flyway aplica V_n+1, V_n+2... em sequência. Confirme que a versão do backend é compatível antes de fazer o restore — rollback de schema não tem migration reversível no Halo.
- Se restaurou `evogo_auth` também (§3 nota), reinicie o Evolution: `docker compose restart evolution-go`. O bot deve voltar ao estado `connected` sem refazer QR.

---

## §5 Reinício controlado dos containers

### §5.A Postgres

**Quando usar:** suspeita de leak (`max_connections` esgotado), congelado, ou para aplicar mudança em variáveis de ambiente.

**Pré-requisitos:** §3 feito recentemente (boa prática) ou na última hora.

**Comandos:**
```bash
docker compose stop backend evolution-go     # quem usa o Postgres para de bater
docker compose restart postgres              # ~2-5 s típico
sleep 5
docker exec halo-postgres pg_isready -U halo -d halo
docker compose start backend evolution-go
```

**Pós-condição:** §1 verde.

### §5.B Backend (Spring)

**Quando usar:** após mudança em `backend/.env`, deploy de nova imagem, suspeita de memory leak.

```bash
docker compose restart backend
# espera 15-30 s para o Spring subir
until curl -sf http://localhost:8080/actuator/health > /dev/null; do
  echo "esperando backend..."; sleep 3
done
echo "backend UP"
```

**Pós-condição:** `/actuator/health` = UP. Logs sem ERROR no startup (`docker logs halo-backend 2>&1 | tail -50`).

### §5.C Evolution Go

**Quando usar:** estado da sessão WhatsApp parece travado mas não desconectado, ou após mudança em variável de ambiente do container.

```bash
docker compose restart evolution-go
sleep 10
curl -sS -H "apikey: $EVOLUTION_API_KEY" \
  http://localhost:8081/instance/all | jq '.[] | select(.name=="halo-bot").connectionStatus'
```

**Pós-condição:** `connectionStatus == open`. Caso contrário → §2.

### §5.D Tudo

**Quando usar:** reset preventivo, após `git pull` da config nova em prod.

```bash
docker compose down                 # NÃO usa -v (preserva o volume halo-pgdata!)
docker compose up -d
```

> ⚠️ **NUNCA** use `docker compose down -v` em prod — `-v` apaga `halo-pgdata`, derrubando licença ativada do Evolution Go (§2 fica obrigatório para recriar tudo) e os dados de gastos dos usuários.

**Pós-condição:** §1 verde para todos os 5 checks.

---

## §6 Inspeção de logs e troubleshooting

**Pré-requisitos:** acesso SSH (em prod) ou shell local (dev).

### §6.A Comandos básicos

```bash
# Últimas 200 linhas (uma vez)
docker logs --tail 200 halo-backend

# Stream em tempo real
docker logs -f halo-backend

# Filtrar por nível (sai do backend Spring com Slf4j)
docker logs halo-backend 2>&1 | grep -E "WARN|ERROR"

# Janela temporal (últimos 30 min)
docker logs --since 30m halo-evolution-go

# Todos os serviços lado a lado
docker compose logs -f --tail=50
```

### §6.B Erros conhecidos e ações

| Sintoma no log | Causa provável | Ação |
|---|---|---|
| `Connection to postgres:5432 refused` no backend | Postgres ainda subindo ou caiu | §1; se persistir, §5.A |
| `flyway... migration failed` | Schema divergiu manualmente | Investigar `flyway_schema_history` no Postgres antes de §4 |
| `evolution... 401` no `EvolutionClient` | `EVOLUTION_INSTANCE_TOKEN` errado | Re-buscar com curl de §2 e §5.B |
| `CallNotPermittedException: CircuitBreaker 'evolution' is OPEN` | 5 falhas consecutivas no envio WhatsApp | Aguardar 30 s (`wait-duration-in-open-state`) ou §5.C |
| Webhook duplicado descartado msgId=... | Esperado — Evolution Go retransmite | Ignorar (info-level, idempotência funcionando) |
| `INVALID_JSON` do Gemini | Modelo respondeu fora do schema | Verificar versão do modelo em `application.yml` (§ai-fallback heurístico cobre) |
| `JWT rejeitado` repetidos | Frontend com JWT antigo ou clock skew | Forçar refresh no cliente; checar `date` no host vs CI |

### §6.C Saúde do cache de classificação

```bash
# As stats são logadas a cada 100 lookups; force buscar a última no live log:
docker logs halo-backend 2>&1 | grep "ClassificationCache stats" | tail -3
# espera-se ver hitRate crescente conforme uso prolongado.
```

### §6.D Métricas básicas de banco

```bash
# Quantos gastos foram registrados nas últimas 24h:
docker exec halo-postgres psql -U halo -d halo -c "
  SELECT COUNT(*) FROM expenses
  WHERE created_at > NOW() - INTERVAL '24 hours'
    AND deleted_at IS NULL;"

# Conexões abertas no Postgres (se vai esgotar):
docker exec halo-postgres psql -U halo -d halo -c "
  SELECT state, COUNT(*) FROM pg_stat_activity
  WHERE datname='halo' GROUP BY state;"
```

---

## Apêndice — Variáveis de ambiente críticas

Antes de qualquer procedimento, confirme que estão setadas:

| Variável | Local | Uso | Como recuperar se perdida |
|---|---|---|---|
| `EVOLUTION_API_KEY` | `infra/.env` | Header `apikey` em rotas globais do Evolution | Gerar nova (`openssl rand -hex 16`) — quebra licença, exige re-ativar |
| `EVOLUTION_INSTANCE_TOKEN` | `backend/.env` | Header `apikey` em `/send/*` por-instância | `curl ... /instance/all \| jq '.[].token'` (com `EVOLUTION_API_KEY`) |
| `POSTGRES_PASSWORD` | `infra/.env` | Conexão JDBC do backend | Apagar volume e recriar — destrutivo, use só em último caso |
| `HALO_AUTH_JWT_SECRET` | `backend/.env` (prod) | Assinatura dos access tokens | Gerar novo (`openssl rand -base64 48`) — invalida sessões web ativas |
| `GEMINI_API_KEY` | `backend/.env` | Chamada ao Gemini | Gerar nova em [aistudio.google.com/apikey](https://aistudio.google.com/apikey) |

---

> Este runbook é **vivo**. Toda vez que um procedimento der trabalho na prática, atualize o passo correspondente para a próxima vez.

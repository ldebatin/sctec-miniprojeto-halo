#!/usr/bin/env bash
# Verifica e prepara o ambiente para rodar docs/qa/release-1-smoke.md.
# Idempotente — pode rodar várias vezes.
#
# Uso:
#   scripts/smoke-prereqs.sh                # só verifica
#   scripts/smoke-prereqs.sh --webhook URL  # também configura o webhook do Evolution
#
# Sai com código 0 quando tudo essencial está OK (avisos com `!` são tolerados).

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# ---------- helpers de output ----------
if [[ -t 1 ]]; then
  GREEN='\033[32m'; RED='\033[31m'; YELLOW='\033[33m'; BOLD='\033[1m'; RESET='\033[0m'
else
  GREEN=''; RED=''; YELLOW=''; BOLD=''; RESET=''
fi
ok()      { printf '%b✓%b %s\n' "$GREEN" "$RESET" "$*"; }
warn()    { printf '%b!%b %s\n' "$YELLOW" "$RESET" "$*"; }
err()     { printf '%b✗%b %s\n' "$RED" "$RESET" "$*" >&2; }
heading() { printf '\n%b== %s ==%b\n' "$BOLD" "$*" "$RESET"; }

# ---------- parse args ----------
WEBHOOK_URL=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --webhook)
      [[ $# -ge 2 ]] || { err "--webhook precisa de um valor"; exit 1; }
      WEBHOOK_URL="$2"; shift 2
      ;;
    -h|--help)
      sed -n '2,11p' "$0" | sed 's/^# \{0,1\}//'
      exit 0
      ;;
    *) err "argumento desconhecido: $1"; exit 1 ;;
  esac
done

# ---------- 1. .env (infra + backend) ----------
heading "Carregando .env"
INFRA_ENV="$REPO_ROOT/infra/.env"
BACKEND_ENV="$REPO_ROOT/backend/.env"
if [[ ! -f "$INFRA_ENV" ]]; then
  err "infra/.env não existe — copie de infra/.env.example e preencha os segredos"
  exit 1
fi
set -a
# shellcheck disable=SC1090
source "$INFRA_ENV"
# backend/.env é opcional para o compose, mas é onde vive
# EVOLUTION_INSTANCE_TOKEN; quando existir, vars do backend ganham
# precedência sobre as do infra (por exemplo, EVOLUTION_API_KEY).
if [[ -f "$BACKEND_ENV" ]]; then
  # shellcheck disable=SC1090
  source "$BACKEND_ENV"
  ok "infra/.env + backend/.env carregados"
else
  warn "backend/.env não existe — copie de backend/.env.example para preencher GEMINI_API_KEY e EVOLUTION_INSTANCE_TOKEN"
fi
set +a
EVOLUTION_BASE_URL_CLI="${EVOLUTION_BASE_URL:-http://localhost:${EVOLUTION_HOST_PORT:-8081}}"
ok "EVOLUTION_BASE_URL=$EVOLUTION_BASE_URL_CLI"

# ---------- 2. docker ----------
heading "Docker"
command -v docker >/dev/null || { err "docker não está no PATH"; exit 1; }
docker info >/dev/null 2>&1 || { err "daemon do docker não responde"; exit 1; }
ok "docker pronto"

# ---------- 3. compose up ----------
heading "docker compose up -d (postgres + evolution-go)"
( cd "$REPO_ROOT/infra" && docker compose up -d ) >/dev/null
ok "containers ativos"

# ---------- 4. postgres healthy ----------
heading "Aguardando postgres ficar healthy"
for i in $(seq 1 30); do
  state=$(docker inspect -f '{{.State.Health.Status}}' halo-postgres 2>/dev/null || echo "missing")
  if [[ "$state" == "healthy" ]]; then ok "postgres healthy"; break; fi
  if [[ $i -eq 30 ]]; then err "postgres não ficou healthy em 30s — veja \`docker logs halo-postgres\`"; exit 1; fi
  sleep 1
done

# ---------- 5. categorias globais (V3 + V4) ----------
heading "Categorias globais (V3 + V4)"
count=$(docker exec halo-postgres psql -U "${POSTGRES_USER:-halo}" -d "${POSTGRES_DB:-halo}" \
  -tAc "SELECT count(*) FROM categories_global;" 2>/dev/null || echo "0")
count=$(echo "$count" | tr -d '[:space:]')
if [[ "$count" -ge 13 ]]; then
  ok "$count categorias globais (esperado >=13: 12 do PRD + Sem categoria)"
elif [[ "$count" -ge 1 ]]; then
  warn "só $count categorias — Flyway talvez não rodou V3+V4. Suba o backend pelo menos 1x para aplicar."
else
  warn "tabela categories_global vazia — backend ainda não rodou nesse Postgres"
fi

# ---------- 6. backend health ----------
heading "Backend (localhost:8080/actuator/health)"
if curl -fsS --max-time 3 http://localhost:8080/actuator/health >/dev/null 2>&1; then
  ok "backend respondendo"
else
  warn "backend não responde — rode em outro terminal:"
  cat <<EOM
    JAVA_HOME=/usr/lib/jvm/java-21-amazon-corretto \\
      PATH=\$JAVA_HOME/bin:\$PATH \\
      mvn -f backend/pom.xml spring-boot:run
EOM
fi

# ---------- 7. Evolution: instância halo-bot ----------
heading "Evolution Go — instância halo-bot"
# O Evolution Go usa endpoints DIFERENTES da Evolution API (Node) — ver
# docs/setup-evolution.md §2.B. Listagem de instâncias é por-Evolution e usa
# a GLOBAL_API_KEY (sem header `instance`).
EVOLUTION_INSTANCE_NAME="${EVOLUTION_INSTANCE:-halo-bot}"
instances_json=$(curl -fsS --max-time 5 -H "apikey: $EVOLUTION_API_KEY" \
  "$EVOLUTION_BASE_URL_CLI/instance/all" 2>&1 || true)
if [[ "$instances_json" != *'"data":'* ]]; then
  err "Evolution Go não respondeu como esperado em /instance/all: $instances_json"
  err "  - Se HTTP 503: licença não ativada — abra http://localhost:8081/manager/login e siga docs/setup-evolution.md §1."
  err "  - Se 401: EVOLUTION_API_KEY no infra/.env está errado."
  exit 1
fi
if [[ "$instances_json" != *"\"name\":\"$EVOLUTION_INSTANCE_NAME\""* ]]; then
  err "Instância '$EVOLUTION_INSTANCE_NAME' não existe — crie via Manager UI ou:"
  echo "    curl -X POST -H 'apikey: \$EVOLUTION_API_KEY' -H 'Content-Type: application/json' \\"
  echo "      -d '{\"name\":\"$EVOLUTION_INSTANCE_NAME\"}' $EVOLUTION_BASE_URL_CLI/instance/create"
  exit 1
fi
# Extrai status de conexão da resposta de /instance/all (o campo `connected: true|false`).
if [[ "$instances_json" == *"\"name\":\"$EVOLUTION_INSTANCE_NAME\""*'"connected":true'* ]] \
   || python3 -c "
import json,sys
data = json.loads('''$instances_json''')
for inst in data.get('data', []):
    if inst.get('name') == '$EVOLUTION_INSTANCE_NAME' and inst.get('connected'):
        sys.exit(0)
sys.exit(1)
" 2>/dev/null; then
  ok "$EVOLUTION_INSTANCE_NAME conectado ao WhatsApp"
else
  warn "$EVOLUTION_INSTANCE_NAME existe mas NÃO está connected — refaça o pareamento via Manager UI"
fi

# Aviso sobre o instance token (necessário pro backend enviar mensagens).
if [[ -z "${EVOLUTION_INSTANCE_TOKEN:-}" ]]; then
  warn "EVOLUTION_INSTANCE_TOKEN não definido em backend/.env — sem ele, o backend não envia confirmações."
  echo "  Recupere com:"
  echo "    curl -H \"apikey: \$EVOLUTION_API_KEY\" $EVOLUTION_BASE_URL_CLI/instance/all | jq -r '.data[]|select(.name==\"$EVOLUTION_INSTANCE_NAME\").token'"
fi

# ---------- 8. webhook ----------
if [[ -n "$WEBHOOK_URL" ]]; then
  heading "Configurando webhook do $EVOLUTION_INSTANCE_NAME → $WEBHOOK_URL"
  # Evolution Go usa POST /instance/connect com webhookUrl no body
  # (docs/setup-evolution.md §6 — formato Go, não Node).
  if curl -fsS --max-time 5 -X POST "$EVOLUTION_BASE_URL_CLI/instance/connect" \
        -H "apikey: $EVOLUTION_API_KEY" \
        -H "instance: $EVOLUTION_INSTANCE_NAME" \
        -H "Content-Type: application/json" \
        -d "{
          \"webhookUrl\": \"$WEBHOOK_URL\",
          \"subscribe\": [\"messages.upsert\"]
        }" >/dev/null; then
    ok "webhook configurado"
  else
    err "/instance/connect falhou — configure manualmente conforme docs/setup-evolution.md §6"
    exit 1
  fi
else
  heading "Webhook (manual)"
  cat <<EOM
O backend roda no HOST (localhost:8080) e o Evolution Go roda num
container — 'localhost' dentro do container aponta pro próprio container,
não pro backend. Use uma das duas opções:

  A) host.docker.internal (mais simples em dev — Docker 20.10+):
     $0 --webhook http://host.docker.internal:8080/webhooks/evolution

  B) ngrok (necessário para testar com WhatsApp real):
     1) Em outro terminal:    ngrok http 8080
     2) Anote a URL https://abcd-1234.ngrok-free.app
     3) $0 --webhook https://abcd-1234.ngrok-free.app/webhooks/evolution
EOM
fi

# ---------- 9. próximo passo ----------
heading "Pronto"
echo "Abra o roteiro: $REPO_ROOT/docs/qa/release-1-smoke.md"
echo "Acompanhe os logs do backend em outro terminal:"
echo "  tail -f logs/backend.log | grep -E 'Mensagem|Confirmação|Fallback|Telefone inválido'"

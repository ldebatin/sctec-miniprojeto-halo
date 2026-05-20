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
INSTANCE_TOKEN="${EVOLUTION_INSTANCE_TOKEN:-}"
if [[ -z "$INSTANCE_TOKEN" ]]; then
  err "EVOLUTION_INSTANCE_TOKEN vazio em infra/.env — pareie a instância no Manager UI primeiro (T-006)"
  exit 1
fi
state_json=$(curl -fsS --max-time 5 -H "apikey: $INSTANCE_TOKEN" \
  "$EVOLUTION_BASE_URL_CLI/instance/connectionState/halo-bot" 2>&1 || true)
if [[ "$state_json" == *'"state":"open"'* ]]; then
  ok "halo-bot conectado ao WhatsApp"
elif [[ "$state_json" == *'"state":'* ]]; then
  warn "halo-bot não está OPEN — resposta: $state_json"
  warn "refaça o pareamento via Manager UI / QR code"
else
  err "Evolution não respondeu como esperado: $state_json"
  exit 1
fi

# ---------- 8. webhook ----------
if [[ -n "$WEBHOOK_URL" ]]; then
  heading "Configurando webhook do halo-bot → $WEBHOOK_URL"
  if curl -fsS --max-time 5 -X POST "$EVOLUTION_BASE_URL_CLI/webhook/set/halo-bot" \
        -H "apikey: $INSTANCE_TOKEN" \
        -H "Content-Type: application/json" \
        -d "{
          \"webhook\": {
            \"enabled\": true,
            \"url\": \"$WEBHOOK_URL\",
            \"headers\": { \"apikey\": \"$EVOLUTION_API_KEY\" },
            \"events\": [\"MESSAGES_UPSERT\"]
          }
        }" >/dev/null; then
    ok "webhook configurado"
  else
    err "set/webhook falhou — verifique URL e instance-token, ou configure manualmente"
    exit 1
  fi
else
  heading "Webhook (manual)"
  cat <<EOM
Para o Evolution conseguir falar com o backend você precisa expor 8080
publicamente. Sugestão:

  1) Em outro terminal:    ngrok http 8080
  2) Anote a URL retornada (https://abcd-1234.ngrok-free.app)
  3) Rode este script de novo com:
     $0 --webhook https://abcd-1234.ngrok-free.app/webhooks/evolution
EOM
fi

# ---------- 9. próximo passo ----------
heading "Pronto"
echo "Abra o roteiro: $REPO_ROOT/docs/qa/release-1-smoke.md"
echo "Acompanhe os logs do backend em outro terminal:"
echo "  tail -f logs/backend.log | grep -E 'Mensagem|Confirmação|Fallback|Telefone inválido'"

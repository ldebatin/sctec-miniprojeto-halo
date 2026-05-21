# Setup do Evolution Go — instância `halo-bot`

Passo a passo para criar a instância `halo-bot` na Evolution Go e parear o número WhatsApp que o Halo vai usar como bot. Este runbook é referenciado pela [T-006](https://github.com/ldebatin/sctec-miniprojeto-halo/issues/6) e atende os 3 critérios da task.

> ⚠️ **Pré-requisito crítico**: tenha em mãos um **chip / conta WhatsApp descartável** (não use o seu número pessoal — o número fica vinculado ao bot até desconectar, e perde acesso a outras sessões do WhatsApp Web).
>
> ⚠️ **Sobre licenciamento do Evolution Go**: ao subir pela primeira vez, **todos os endpoints `/instance/*` retornam HTTP 503 até a licença ser ativada** via Manager UI. A ativação é um passo manual, único, feito antes de qualquer chamada de API.

---

## 0. Pré-requisitos

- T-004 concluída (compose dev pronto em `infra/`).
- `infra/.env` configurado com `EVOLUTION_API_KEY` definido (gere com `openssl rand -hex 16`).
- Postgres e Evolution Go saudáveis:
  ```bash
  cd infra
  cp .env.example .env  # ajuste EVOLUTION_API_KEY
  docker compose up -d
  docker compose ps     # ambos devem aparecer (healthy)
  ```

A partir daqui usaremos a variável `API_KEY` no shell para encurtar os exemplos:
```bash
export API_KEY=$(grep EVOLUTION_API_KEY infra/.env | cut -d= -f2)
```

---

## 1. Ativar a licença (uma única vez)

Sem isso, qualquer chamada `/instance/...` responde `503 Service Unavailable`.

1. Abra **`http://localhost:8080/manager/login`** no navegador.
2. Em **API URL** informe `http://localhost:8080`.
3. Em **API Key** cole o valor de `EVOLUTION_API_KEY` (mesmo do `.env`).
4. Siga o fluxo de registro/ativação da licença do Evolution Go (cadastro do projeto na conta Evolution).
5. Após ativação, o manager mostra a tela inicial de instâncias.

> Você pode validar que destravou batendo:
> ```bash
> curl -sS -o /dev/null -w "%{http_code}\n" -H "apikey: $API_KEY" http://localhost:8080/instance/all
> # antes da ativação: 503
> # depois da ativação: 200
> ```

---

## 2. Criar a instância `halo-bot`

Duas opções equivalentes — escolha uma.

### 2.A — via Manager UI (mais fácil para o primeiro setup)

1. No manager (`/manager`), clique em **"Create Instance"** (ou equivalente).
2. **Name**: `halo-bot`
3. Confirme. A UI já gera a instância e abre o QR.

### 2.B — via API (útil para automação/reprovisionamento)

```bash
curl -sS -X POST http://localhost:8080/instance/create \
  -H "Content-Type: application/json" \
  -H "apikey: $API_KEY" \
  -d '{"name":"halo-bot"}'
```

Resposta esperada (200):
```json
{ "instance": { "name": "halo-bot", ... } }
```

> **Observação sobre nomes de rotas:** A descrição original da T-006 menciona `POST /message/sendText/halo-bot` — esse é o padrão da Evolution **API (Node.js)**. A versão Go usa rotas **sem `instance` no path**: `POST /send/text`, `GET /instance/qr`, `GET /instance/status`, com o nome/contexto da instância passado no body ou via header `instance` (ver [swagger](http://localhost:8080/swagger/index.html)).

---

## 3. Parear o WhatsApp (escanear o QR)

### 3.A — pelo Manager UI

1. Na lista de instâncias, clique em `halo-bot`.
2. A UI mostra o QR code (atualiza automaticamente).
3. No celular descartável: WhatsApp → ⋮ → **"Aparelhos conectados"** → **"Conectar um aparelho"** → escanear.
4. Em poucos segundos a tela do manager troca para "connected".

### 3.B — pela API (precisa abrir o QR no terminal)

```bash
# 1. Disparar a conexão
curl -sS -X POST http://localhost:8080/instance/connect \
  -H "Content-Type: application/json" \
  -H "apikey: $API_KEY" \
  -H "instance: halo-bot" \
  -d '{}'

# 2. Buscar o QR (a resposta contém o conteúdo base64 e/ou string do QR)
curl -sS -H "apikey: $API_KEY" -H "instance: halo-bot" \
  http://localhost:8080/instance/qr
```

Renderize o QR (por exemplo, salvando o base64 PNG e abrindo) e escaneie no celular descartável. O QR expira em ~30 s — repita o `GET /instance/qr` se necessário até pareamento bem-sucedido.

---

## 4. Confirmar que está `connected`

```bash
curl -sS -H "apikey: $API_KEY" -H "instance: halo-bot" \
  http://localhost:8080/instance/status
```

Resposta esperada (200) — campo de estado é `"connected"` (ou equivalente). Se ainda estiver em `connecting`, espere ~5 s e tente de novo.

✅ **Atende:** *"Instância `halo-bot` aparece como `connected` na API Evolution."*

---

## 5. Enviar mensagem de teste

Substitua `5547999999999` pelo número de destino (sem `+`, com código do país). Pode mandar para o próprio número que acabou de parear ou para um terceiro autorizado.

> **⚠ Auth correto** (validado empiricamente — `/send/text` rejeita a global
> api-key com 401 e exige o **instance token** retornado por `/instance/create`,
> bater com CLAUDE.md §"Evolution Go — modelo de auth em 2 níveis"):
> ```bash
> export INSTANCE_TOKEN=$(curl -sS -H "apikey: $API_KEY" \
>   http://localhost:8080/instance/all | jq -r '.data[]|select(.name=="halo-bot").token')
> ```

```bash
curl -sS -X POST http://localhost:8080/send/text \
  -H "Content-Type: application/json" \
  -H "apikey: $INSTANCE_TOKEN" \
  -H "instance: halo-bot" \
  -d '{
        "number": "5547999999999",
        "text": "Halo bot online 👋 — teste T-006"
      }'
```

Resposta esperada (200): payload com o ID da mensagem. Confirme visualmente no celular de destino.

✅ **Atende:** *"Envio de mensagem de teste via `POST /send/text` funciona."* (A criterion original mencionava `POST /message/sendText/halo-bot` — ver nota da §2.B sobre a diferença Go vs Node.)

---

## 6. (Opcional) Apontar o webhook para o backend

Quando T-008 estiver no ar, configure o webhook para receber eventos no backend Halo. O endpoint **não tem auth dedicada** — o Evolution Go self-hosted não envia headers de auth em webhooks de saída (issue upstream #1933, closed as not-planned). A proteção é de rede / reverse proxy à frente do backend.

```bash
curl -sS -X POST http://localhost:8080/instance/connect \
  -H "Content-Type: application/json" \
  -H "apikey: $API_KEY" \
  -H "instance: halo-bot" \
  -d '{
        "webhookUrl": "http://host.docker.internal:8080/webhooks/evolution",
        "subscribe": ["messages.upsert"]
      }'
```

Em produção, troque por `https://api.halo.<domain>/webhooks/evolution` e configure `EVOLUTION_WEBHOOK_URL` no `infra/.env`.

---

## 7. Operação do dia-a-dia

| Ação | Comando |
|---|---|
| Ver status | `curl -H "apikey: $API_KEY" -H "instance: halo-bot" localhost:8080/instance/status` |
| Listar instâncias | `curl -H "apikey: $API_KEY" localhost:8080/instance/all` |
| Reconectar (sem novo QR) | `curl -X POST -H "apikey: $API_KEY" -H "instance: halo-bot" localhost:8080/instance/reconnect` |
| Forçar novo QR | `curl -X POST -H "apikey: $API_KEY" -H "instance: halo-bot" localhost:8080/instance/forcereconnect/halo-bot` |
| Logout (mantém instância) | `curl -X DELETE -H "apikey: $API_KEY" -H "instance: halo-bot" localhost:8080/instance/logout` |
| Apagar instância | `curl -X DELETE -H "apikey: $API_KEY" localhost:8080/instance/delete/halo-bot` |

---

## 8. Troubleshooting

| Sintoma | Causa provável | Ação |
|---|---|---|
| `503` em `/instance/all` | Licença não ativada | Voltar à **§1** |
| `400 Bad Request` em `/instance/create` | Body inválido ou licença pendente | Conferir JSON / refazer **§1** |
| QR não aparece / expira instantaneamente | `halo-evolution-go` não consegue alcançar Postgres | `docker compose logs evolution-go` |
| `connected` cai depois de minutos | Celular ficou offline / outra sessão de WhatsApp Web roubou o slot | Abrir manager UI e refazer pareamento |
| Mensagem volta como falha de envio | Número de destino bloqueou o bot ou nunca conversou antes | Em conta WhatsApp não-business, o destinatário precisa conversar primeiro |
| Banco do Evolution corrompeu | Conflito de migration em `evogo_auth` / `evogo_users` | `docker compose down && docker volume rm halo-pgdata && docker compose up -d` *(perde também o `halo` DB — reaplicar Flyway depois)* |

---

## 9. Variáveis de ambiente relevantes

| Var | Onde | Para quê |
|---|---|---|
| `EVOLUTION_API_KEY` | `infra/.env` + `backend/.env` | Mesmo valor do `GLOBAL_API_KEY` do container. Usado no header `apikey` das chamadas globais (`/instance/all`, `/instance/create`) e na ativação da licença. Não tem nada a ver com o webhook que CHEGA no backend (esse fica sem auth dedicada — Evolution Go não envia headers em webhooks de saída). |
| `EVOLUTION_HOST_PORT` | `infra/.env` | Porta exposta no host. Default 8080 — troque (ex: 8088) se for rodar o backend Spring junto em dev. |
| `EVOLUTION_WEBHOOK_URL` | `infra/.env` | URL do backend que receberá o webhook. Vazia em dev sem túnel. |
| `EVOLUTION_BASE_URL` | `backend/.env` | URL pela qual o backend chama o Evolution. Em compose: `http://evolution-go:8080`. |
| `EVOLUTION_INSTANCE_TOKEN` | `backend/.env` | Token retornado por `POST /instance/create`. Necessário para endpoints POR INSTÂNCIA (ver CLAUDE.md §"Evolution Go — modelo de auth em 2 níveis"). |

-- =============================================================================
-- V1__init.sql — schema inicial do Halo
--
-- Cobre todas as entidades descritas em docs/analise-tecnica.md §6:
-- users, categories_global, categories, expenses, otp_codes, refresh_tokens,
-- whatsapp_messages, conversation_state, ai_log.
--
-- Convenções:
--   * PKs em UUID (geradas v7 pela aplicação Java; gen_random_uuid disponível
--     como fallback para inserts manuais em dev).
--   * Enums modelados como VARCHAR + CHECK (mais flexível que enum nativo e
--     mais simples de mapear no JPA com @Enumerated(EnumType.STRING)).
--   * Timestamps com timezone (TIMESTAMPTZ) sempre — UTC armazenado pelo
--     Hibernate (hibernate.jdbc.time_zone=UTC, ver application.yml).
--   * Soft delete em expenses via deleted_at nullable (§6.2 + critério T-005).
-- =============================================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- -----------------------------------------------------------------------------
-- users — conta única por telefone (E.164 normalizado pela aplicação)
-- -----------------------------------------------------------------------------
CREATE TABLE users (
    id          UUID PRIMARY KEY,
    name        VARCHAR(120) NOT NULL,
    phone       VARCHAR(20)  NOT NULL UNIQUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- -----------------------------------------------------------------------------
-- categories_global — categorias semeadas, visíveis para todos
-- -----------------------------------------------------------------------------
CREATE TABLE categories_global (
    id        UUID PRIMARY KEY,
    name      VARCHAR(60) NOT NULL UNIQUE,
    icon      VARCHAR(60) NOT NULL,
    color     VARCHAR(20) NOT NULL,
    keywords  TEXT[]      NOT NULL DEFAULT ARRAY[]::TEXT[]
);

-- -----------------------------------------------------------------------------
-- categories — categorias por usuário; global_id liga ao seed (override RF-08)
-- -----------------------------------------------------------------------------
CREATE TABLE categories (
    id          UUID PRIMARY KEY,
    user_id     UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    global_id   UUID         REFERENCES categories_global(id) ON DELETE SET NULL,
    name        VARCHAR(60)  NOT NULL,
    icon        VARCHAR(60)  NOT NULL,
    color       VARCHAR(20)  NOT NULL,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    -- RF-07: dois nomes ativos iguais não podem coexistir para o mesmo usuário
    CONSTRAINT uq_categories_user_active_name UNIQUE (user_id, name, active)
);

CREATE INDEX idx_categories_user_id   ON categories(user_id);
CREATE INDEX idx_categories_global_id ON categories(global_id);

-- -----------------------------------------------------------------------------
-- expenses — lançamentos; source enum (WHATSAPP|WEB); soft delete via deleted_at
-- -----------------------------------------------------------------------------
CREATE TABLE expenses (
    id           UUID            PRIMARY KEY,
    user_id      UUID            NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    category_id  UUID            NOT NULL REFERENCES categories(id),
    description  VARCHAR(255)    NOT NULL,
    amount       NUMERIC(12,2)   NOT NULL CHECK (amount > 0),
    occurred_at  DATE            NOT NULL,
    source       VARCHAR(16)     NOT NULL CHECK (source IN ('WHATSAPP','WEB')),
    raw_message  TEXT,
    deleted_at   TIMESTAMPTZ,
    created_at   TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ     NOT NULL DEFAULT now()
);

-- Consultas frequentes: resumos mensais e listagens por categoria. Os índices
-- abaixo excluem soft-deleted (partial index) para deixar as consultas do
-- dashboard/relatório enxutas.
CREATE INDEX idx_expenses_user_occurred
    ON expenses(user_id, occurred_at DESC)
    WHERE deleted_at IS NULL;
CREATE INDEX idx_expenses_user_category
    ON expenses(user_id, category_id)
    WHERE deleted_at IS NULL;

-- -----------------------------------------------------------------------------
-- otp_codes — hash bcrypt do código de 6 dígitos (RF-09); TTL 5min, max 5
-- tentativas; phone armazenado em E.164 (sem usuário, pois login pode ocorrer
-- antes do cadastro)
-- -----------------------------------------------------------------------------
CREATE TABLE otp_codes (
    id          UUID         PRIMARY KEY,
    phone       VARCHAR(20)  NOT NULL,
    code_hash   VARCHAR(120) NOT NULL,
    expires_at  TIMESTAMPTZ  NOT NULL,
    used_at     TIMESTAMPTZ,
    attempts    INTEGER      NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_otp_codes_phone_created ON otp_codes(phone, created_at DESC);

-- -----------------------------------------------------------------------------
-- refresh_tokens — sessão web de 30 dias (RF-09); revoked_at para logout
-- -----------------------------------------------------------------------------
CREATE TABLE refresh_tokens (
    id          UUID         PRIMARY KEY,
    user_id     UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash  VARCHAR(120) NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ  NOT NULL,
    revoked_at  TIMESTAMPTZ,
    user_agent  VARCHAR(255),
    ip          VARCHAR(45),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_refresh_tokens_user ON refresh_tokens(user_id);

-- -----------------------------------------------------------------------------
-- whatsapp_messages — log bidirecional; evolution_msg_id UNIQUE garante
-- idempotência do webhook (§6.2)
-- -----------------------------------------------------------------------------
CREATE TABLE whatsapp_messages (
    id                UUID         PRIMARY KEY,
    user_id           UUID         REFERENCES users(id) ON DELETE SET NULL,
    evolution_msg_id  VARCHAR(120) NOT NULL UNIQUE,
    direction         VARCHAR(8)   NOT NULL CHECK (direction IN ('IN','OUT')),
    content           TEXT,
    status            VARCHAR(32)  NOT NULL,
    received_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    processed_at      TIMESTAMPTZ
);

CREATE INDEX idx_whatsapp_messages_user_received
    ON whatsapp_messages(user_id, received_at DESC);

-- -----------------------------------------------------------------------------
-- conversation_state — estado conversacional por usuário (1:1); TTL aplicado
-- na aplicação via expires_at, com job de limpeza diário (§6.2)
-- -----------------------------------------------------------------------------
CREATE TABLE conversation_state (
    user_id     UUID         PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    state       VARCHAR(32)  NOT NULL,
    payload     JSONB        NOT NULL DEFAULT '{}'::jsonb,
    expires_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_conversation_state_expires ON conversation_state(expires_at);

-- -----------------------------------------------------------------------------
-- ai_log — auditoria de chamadas ao Gemini (§3.2 das métricas do PRD)
-- -----------------------------------------------------------------------------
CREATE TABLE ai_log (
    id           UUID          PRIMARY KEY,
    user_id      UUID          REFERENCES users(id) ON DELETE SET NULL,
    model        VARCHAR(60)   NOT NULL,
    prompt_hash  VARCHAR(64)   NOT NULL,
    tokens_in    INTEGER       NOT NULL DEFAULT 0 CHECK (tokens_in >= 0),
    tokens_out   INTEGER       NOT NULL DEFAULT 0 CHECK (tokens_out >= 0),
    latency_ms   INTEGER       NOT NULL DEFAULT 0 CHECK (latency_ms >= 0),
    status       VARCHAR(32)   NOT NULL,
    cost_est     NUMERIC(10,6) NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_ai_log_user_created ON ai_log(user_id, created_at DESC);

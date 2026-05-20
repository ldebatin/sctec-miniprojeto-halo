-- =============================================================================
-- V2__conversation_state_by_phone.sql
--
-- Reestrutura conversation_state para suportar o estado AWAITING_NAME ANTES de
-- o usuário existir (fluxo do PRD §7.1 / T-011). O schema original (V1) tinha
-- user_id como PK com FK obrigatória para users, o que impedia gravar o estado
-- na primeira mensagem de um telefone desconhecido.
--
-- Mudanças:
--   * PK agora é uma coluna `id` UUID própria (alinhada com as demais tabelas).
--   * `phone` VARCHAR(20) UNIQUE NOT NULL — identifica a conversa antes/depois
--     do cadastro (mesmo formato E.164 de users.phone).
--   * `user_id` continua FK para users(id), mas vira NULLABLE (preenchido após
--     o cadastro).
--
-- A tabela está vazia em dev/staging (ninguém escreveu nela ainda), então não
-- há dados para migrar.
-- =============================================================================

ALTER TABLE conversation_state DROP CONSTRAINT conversation_state_pkey;
ALTER TABLE conversation_state DROP CONSTRAINT conversation_state_user_id_fkey;
ALTER TABLE conversation_state ALTER COLUMN user_id DROP NOT NULL;

ALTER TABLE conversation_state
    ADD COLUMN id    UUID         NOT NULL DEFAULT gen_random_uuid(),
    ADD COLUMN phone VARCHAR(20)  NOT NULL DEFAULT '';

ALTER TABLE conversation_state ALTER COLUMN id DROP DEFAULT;
ALTER TABLE conversation_state ALTER COLUMN phone DROP DEFAULT;

ALTER TABLE conversation_state ADD CONSTRAINT conversation_state_pkey PRIMARY KEY (id);
ALTER TABLE conversation_state ADD CONSTRAINT uq_conversation_state_phone UNIQUE (phone);
ALTER TABLE conversation_state
    ADD CONSTRAINT conversation_state_user_id_fkey
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

-- O índice idx_conversation_state_expires (V1) sobrevive ao ALTER e segue
-- sendo o usado pelo job de limpeza diário.

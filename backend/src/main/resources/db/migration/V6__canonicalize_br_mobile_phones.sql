-- =============================================================================
-- V6__canonicalize_br_mobile_phones.sql
--
-- Resolve a inconsistência do "9 móvel" brasileiro nos dados existentes.
--
-- Contexto: o Evolution Go envia JIDs no formato LEGADO de 12 dígitos
-- (+55 + DDD + 8 dígitos) para celulares antigos, então usuários cadastrados
-- via WhatsApp ficaram com phone sem o 9 obrigatório após o DDD. Já o
-- formulário de login web sempre envia o formato MODERNO de 13 dígitos
-- (+55 + DDD + 9 + 8 dígitos), o que impedia o lookup em /auth/otp/verify.
--
-- A canonicalização para o formato moderno passou a ser feita pelo
-- PhoneNumberService a partir desta versão (vide canonicalizeBrazilianMobile).
-- Esta migration alinha os dados antigos com a nova regra.
--
-- Regra (espelha PhoneNumberService): se phone começa com '+55', tem 13
-- caracteres (12 dígitos pós '+') e o primeiro dígito do número local está
-- em 6–9, insere '9' entre o DDD e o número local.
-- =============================================================================

UPDATE users
SET phone = substring(phone FROM 1 FOR 5) || '9' || substring(phone FROM 6)
WHERE phone LIKE '+55__________'              -- '+55' + exatamente 10 dígitos
  AND length(phone) = 13
  AND substring(phone FROM 6 FOR 1) BETWEEN '6' AND '9';

UPDATE otp_codes
SET phone = substring(phone FROM 1 FOR 5) || '9' || substring(phone FROM 6)
WHERE phone LIKE '+55__________'
  AND length(phone) = 13
  AND substring(phone FROM 6 FOR 1) BETWEEN '6' AND '9';

UPDATE conversation_state
SET phone = substring(phone FROM 1 FOR 5) || '9' || substring(phone FROM 6)
WHERE phone LIKE '+55__________'
  AND length(phone) = 13
  AND substring(phone FROM 6 FOR 1) BETWEEN '6' AND '9';

-- -----------------------------------------------------------------------------
-- V8__br_mobile_ninth_digit_backfill.sql
--
-- Backfill: insere o 9º dígito em celulares brasileiros gravados no formato
-- legado (+55 + DDD + 8 dígitos começando com 8 ou 9). Reconcilia divergência
-- entre telefones vindos do JID Evolution Go (chegam sem o 9) e o que o
-- usuário digita no login OTP da web (com o 9). A partir desta migration o
-- PhoneNumberService também insere o 9 na entrada, então linhas novas já
-- nascem no formato canônico.
-- -----------------------------------------------------------------------------

UPDATE users
   SET phone = '+55' || SUBSTRING(phone FROM 4 FOR 2) || '9' || SUBSTRING(phone FROM 6)
 WHERE phone ~ '^\+55[0-9]{2}[89][0-9]{7}$';

UPDATE otp_codes
   SET phone = '+55' || SUBSTRING(phone FROM 4 FOR 2) || '9' || SUBSTRING(phone FROM 6)
 WHERE phone ~ '^\+55[0-9]{2}[89][0-9]{7}$';

UPDATE conversation_state
   SET phone = '+55' || SUBSTRING(phone FROM 4 FOR 2) || '9' || SUBSTRING(phone FROM 6)
 WHERE phone ~ '^\+55[0-9]{2}[89][0-9]{7}$';

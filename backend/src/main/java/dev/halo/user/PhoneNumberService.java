package dev.halo.user;

import org.springframework.stereotype.Service;

/**
 * Normalização de telefones para E.164 (RF-02, analise-tecnica.md §6.1).
 *
 * Aceita entradas vindas do Evolution Go ({@code 5547999999999@s.whatsapp.net},
 * com ou sem device id {@code :1}) ou digitadas pelo usuário ({@code +5547999999999},
 * {@code 5547999999999}, com espaços/parênteses/traços) e devolve sempre o
 * formato canônico {@code +<DDI><número>} usado em {@code users.phone}.
 *
 * Reconciliação do 9º dígito (celular brasileiro): JIDs antigos do WhatsApp
 * chegam no formato legado (12 dígitos: {@code 55<DDD><8 dígitos>}), enquanto
 * o que o usuário digita hoje tem 13 (com o 9 prefixado no número local).
 * Para o mesmo telefone bater entre webhook do Evolution e login OTP da web,
 * normalizamos sempre para a forma com o 9.
 */
@Service
public class PhoneNumberService {

    /** Mínimo realista de dígitos para um telefone internacional (E.164 permite 8 a 15). */
    private static final int E164_MIN_DIGITS = 8;
    private static final int E164_MAX_DIGITS = 15;

    /**
     * Normaliza qualquer representação de telefone aceita pelo Halo para E.164.
     *
     * @throws InvalidPhoneException quando a entrada é nula, em branco ou não
     *         contém entre 8 e 15 dígitos após remover decoração.
     */
    public String normalize(String input) {
        if (input == null || input.isBlank()) {
            throw new InvalidPhoneException("telefone nulo ou em branco");
        }

        String trimmed = input.trim();

        // Remove sufixo JID do WhatsApp (e o device id opcional antes do @, ex.: ":1@s.whatsapp.net")
        int atIdx = trimmed.indexOf('@');
        if (atIdx >= 0) {
            trimmed = trimmed.substring(0, atIdx);
        }
        int colonIdx = trimmed.indexOf(':');
        if (colonIdx >= 0) {
            trimmed = trimmed.substring(0, colonIdx);
        }

        String digits = trimmed.replaceAll("[^0-9]", "");

        if (digits.length() < E164_MIN_DIGITS || digits.length() > E164_MAX_DIGITS) {
            throw new InvalidPhoneException(
                    "telefone fora do intervalo E.164 (8–15 dígitos): '" + input + "'");
        }

        digits = ensureBrazilianMobileNinthDigit(digits);

        return "+" + digits;
    }

    /**
     * Insere o 9 inicial em celulares brasileiros recebidos no formato legado
     * (12 dígitos: {@code 55 + DDD + 8 dígitos começando com 8 ou 9}). Fixos
     * brasileiros começam com 2–5 — não são afetados.
     */
    private String ensureBrazilianMobileNinthDigit(String digits) {
        if (digits.length() != 12 || !digits.startsWith("55")) {
            return digits;
        }
        char firstLocal = digits.charAt(4);
        if (firstLocal != '8' && firstLocal != '9') {
            return digits;
        }
        return digits.substring(0, 4) + "9" + digits.substring(4);
    }
}

package dev.halo.expense;

import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Heurística de extração de valor monetário a partir de texto livre, usada
 * pelo fallback do parser quando o Gemini não responde (T-016).
 *
 * Cobre os formatos mais comuns que o usuário brasileiro escreve:
 * <ul>
 *   <li>{@code "Mercado 87,30"} → {@code 87.30}</li>
 *   <li>{@code "Uber 25"} → {@code 25}</li>
 *   <li>{@code "Mercado R$ 1.234,56"} → {@code 1234.56}</li>
 *   <li>{@code "Mercado 87.30"} → {@code 87.30}</li>
 *   <li>{@code "Mercado 1.234"} → {@code 1234}</li>
 * </ul>
 *
 * Convenção pt-BR: quando há tanto {@code ,} quanto {@code .}, a vírgula é o
 * separador decimal e o ponto é separador de milhar. Quando há apenas
 * {@code .}, distinguimos pelo número de dígitos após o último ponto
 * (2 → decimal, 3 → milhar).
 */
public final class AmountExtractor {

    private static final Pattern NUMBER_PATTERN =
            Pattern.compile("\\d{1,3}(?:[.,]\\d+)+|\\d+");

    private AmountExtractor() {}

    /**
     * Devolve o primeiro valor positivo encontrado no texto, ou {@code null}
     * se não houver número reconhecível.
     */
    public static BigDecimal extract(String text) {
        if (text == null || text.isBlank()) return null;

        Matcher matcher = NUMBER_PATTERN.matcher(text);
        if (!matcher.find()) return null;

        String raw = matcher.group();
        try {
            return new BigDecimal(normalize(raw));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static String normalize(String raw) {
        if (raw.contains(",")) {
            // pt-BR: vírgula é decimal, ponto é milhar.
            return raw.replace(".", "").replace(",", ".");
        }
        if (!raw.contains(".")) {
            return raw;
        }
        // Só ponto: distinguir decimal de milhar pelo nº de dígitos após o último ponto.
        int lastDot = raw.lastIndexOf('.');
        int digitsAfter = raw.length() - lastDot - 1;
        if (digitsAfter <= 2 && raw.indexOf('.') == lastDot) {
            // Único ponto com até 2 dígitos depois → decimal (ex.: "87.30").
            return raw;
        }
        // Pontos como milhar (ex.: "1.234", "1.234.567") — descarta os pontos.
        return raw.replace(".", "");
    }
}

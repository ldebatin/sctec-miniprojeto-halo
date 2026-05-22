package dev.halo.report;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.Locale;

/**
 * Formata um {@link ReportDtos.MonthlyResponse} como mensagem WhatsApp
 * (RF-15, T-040). O corpo da tabela vai entre três crases — o WhatsApp
 * renderiza como código monoespaçado, preservando o alinhamento.
 *
 * Exemplo de saída:
 * <pre>
 * 📊 Resumo de Maio de 2026
 *
 * ```
 * Categoria       Valor      %
 * ─────────────────────────────
 * Mercado     R$    400,00  80%
 * Lazer       R$    100,00  20%
 * ─────────────────────────────
 * Total       R$    500,00
 * ```
 * </pre>
 *
 * Limites do WhatsApp são de ~4096 chars/mensagem — a tabela cabe
 * tranquilamente mesmo com dezenas de categorias.
 */
public final class WhatsappReportFormatter {

    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");
    /** Largura máxima da coluna de categoria — corta nomes muito longos. */
    private static final int CATEGORY_WIDTH = 12;
    private static final int VALUE_WIDTH = 11;   // "R$ 9.999,99"
    private static final int PERCENT_WIDTH = 4;  // "100%"

    private WhatsappReportFormatter() {}

    public static String format(ReportDtos.MonthlyResponse report) {
        String header = "📊 Resumo de " + monthNamePtBr(report.from());

        if (report.breakdown().isEmpty()) {
            return header + "\n\nNada registrado neste mês.";
        }

        StringBuilder sb = new StringBuilder(header).append("\n\n```\n");
        sb.append(formatRow("Categoria", "Valor", "%")).append('\n');
        sb.append(separator()).append('\n');
        for (ReportDtos.Category c : report.breakdown()) {
            sb.append(formatRow(
                    truncate(c.name(), CATEGORY_WIDTH),
                    formatMoney(c.total()),
                    formatPercent(c.percentage()))).append('\n');
        }
        sb.append(separator()).append('\n');
        sb.append(formatRow("Total", formatMoney(report.total()), "")).append('\n');
        sb.append("```");
        return sb.toString();
    }

    /** Ex.: {@code 2026-05-15} → {@code Maio de 2026}. */
    public static String monthNamePtBr(LocalDate date) {
        String month = date.getMonth().getDisplayName(TextStyle.FULL, PT_BR);
        return capitalize(month) + " de " + date.getYear();
    }

    /** {@code 1234.56} → {@code R$  1.234,56}. */
    static String formatMoney(BigDecimal amount) {
        NumberFormat money = NumberFormat.getNumberInstance(PT_BR);
        money.setMinimumFractionDigits(2);
        money.setMaximumFractionDigits(2);
        String number = money.format(amount);
        return ("R$ " + number);
    }

    /** {@code 80.00} → {@code 80%}. Inteiros só, para ocupar pouco espaço. */
    static String formatPercent(BigDecimal percentage) {
        return percentage.setScale(0, java.math.RoundingMode.HALF_UP) + "%";
    }

    private static String formatRow(String category, String value, String percent) {
        return padRight(category, CATEGORY_WIDTH)
                + " " + padLeft(value, VALUE_WIDTH)
                + " " + padLeft(percent, PERCENT_WIDTH);
    }

    private static String separator() {
        return "─".repeat(CATEGORY_WIDTH + 1 + VALUE_WIDTH + 1 + PERCENT_WIDTH);
    }

    private static String truncate(String s, int max) {
        if (s.length() <= max) return s;
        return s.substring(0, max - 1) + "…";
    }

    private static String padRight(String s, int width) {
        if (s.length() >= width) return s;
        return s + " ".repeat(width - s.length());
    }

    private static String padLeft(String s, int width) {
        if (s.length() >= width) return s;
        return " ".repeat(width - s.length()) + s;
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}

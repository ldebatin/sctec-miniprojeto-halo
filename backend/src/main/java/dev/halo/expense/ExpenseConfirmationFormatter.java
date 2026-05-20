package dev.halo.expense;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Monta a mensagem de confirmação de gasto em pt-BR (RF-04).
 *
 * Padrão exato (T-015):
 * {@code Registrado: <descrição> R$ <valor> → <categoria>. Data: <DD/MM>. Use a web para alterar.}
 *
 * Valor sempre em pt-BR ({@code 1.234,56}), data sem ano ({@code DD/MM}).
 */
public final class ExpenseConfirmationFormatter {

    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");
    private static final DateTimeFormatter DAY_MONTH = DateTimeFormatter.ofPattern("dd/MM");

    private ExpenseConfirmationFormatter() {}

    public static String format(
            String description, BigDecimal amount, String categoryName, LocalDate occurredAt) {
        return "Registrado: %s R$ %s → %s. Data: %s. Use a web para alterar.".formatted(
                description,
                formatAmount(amount),
                categoryName,
                DAY_MONTH.format(occurredAt));
    }

    static String formatAmount(BigDecimal amount) {
        NumberFormat money = NumberFormat.getNumberInstance(PT_BR);
        money.setMinimumFractionDigits(2);
        money.setMaximumFractionDigits(2);
        return money.format(amount);
    }
}

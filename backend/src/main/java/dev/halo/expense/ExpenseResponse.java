package dev.halo.expense;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Representação HTTP de um {@link Expense}. Público porque é reusado fora
 * do módulo (ex.: {@code report/} em T-039).
 */
public record ExpenseResponse(
        UUID id,
        String description,
        BigDecimal amount,
        UUID categoryId,
        LocalDate occurredAt,
        ExpenseSource source,
        Instant createdAt,
        Instant updatedAt
) {
    public static ExpenseResponse from(Expense e) {
        return new ExpenseResponse(
                e.getId(),
                e.getDescription(),
                e.getAmount(),
                e.getCategoryId(),
                e.getOccurredAt(),
                e.getSource(),
                e.getCreatedAt(),
                e.getUpdatedAt());
    }
}

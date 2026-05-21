package dev.halo.report;

import dev.halo.expense.ExpenseResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * DTOs HTTP do {@code /reports} (RF-12, RF-15 — T-039).
 *
 * Centralizado para que o monthly e o categories evoluam juntos — o
 * primeiro reutiliza {@link Category} para o breakdown.
 */
final class ReportDtos {

    private ReportDtos() {}

    /**
     * Resposta de {@code GET /reports/monthly}: total do mês, breakdown por
     * categoria (ordenado desc) com % do total, e a lista de gastos do
     * período (ordenada por {@code occurred_at desc}).
     */
    record MonthlyResponse(
            String month,
            LocalDate from,
            LocalDate to,
            BigDecimal total,
            List<Category> breakdown,
            List<ExpenseResponse> expenses
    ) {}

    /** Resposta de {@code GET /reports/categories}: breakdown no período. */
    record CategoriesResponse(
            LocalDate from,
            LocalDate to,
            BigDecimal total,
            List<Category> breakdown
    ) {}

    /**
     * Linha do breakdown. {@code percentage} é {@code total / soma} arredondado
     * para 2 casas — soma de todas as linhas pode não fechar exatamente em
     * 100% por causa de arredondamento (esperado).
     */
    record Category(
            UUID categoryId,
            String name,
            String color,
            BigDecimal total,
            BigDecimal percentage
    ) {}
}

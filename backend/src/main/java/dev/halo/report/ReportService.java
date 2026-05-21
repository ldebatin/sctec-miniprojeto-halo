package dev.halo.report;

import dev.halo.expense.ExpenseResponse;
import dev.halo.expense.ExpenseRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Agregações de relatório (RF-12, RF-15, T-039).
 *
 * Usa a query {@code aggregateByCategory} do {@link ExpenseRepository} —
 * JPQL com JOIN explícito porque {@code Expense.categoryId} é
 * {@code UUID}, não associação JPA. Gastos soft-deletados são filtrados
 * direto na query.
 *
 * Cálculo de {@code percentage}: {@code categoryTotal / total} arredondado
 * para 2 casas (HALF_UP). Soma das % pode não fechar 100 por arredondamento.
 */
@Service
@RequiredArgsConstructor
public class ReportService {

    private final ExpenseRepository expenseRepository;

    @Transactional(readOnly = true)
    public ReportDtos.MonthlyResponse monthly(UUID userId, YearMonth yearMonth) {
        LocalDate from = yearMonth.atDay(1);
        LocalDate to = yearMonth.atEndOfMonth();

        List<ReportDtos.Category> breakdown = breakdown(userId, from, to);
        BigDecimal total = sumOf(breakdown);

        List<ExpenseResponse> expenses = expenseRepository
                .findByUserIdAndDeletedAtIsNullAndOccurredAtBetweenOrderByOccurredAtDescCreatedAtDesc(
                        userId, from, to)
                .stream()
                .map(ExpenseResponse::from)
                .toList();

        return new ReportDtos.MonthlyResponse(
                yearMonth.toString(), from, to, total, breakdown, expenses);
    }

    @Transactional(readOnly = true)
    public ReportDtos.CategoriesResponse categories(UUID userId, LocalDate from, LocalDate to) {
        List<ReportDtos.Category> breakdown = breakdown(userId, from, to);
        BigDecimal total = sumOf(breakdown);
        return new ReportDtos.CategoriesResponse(from, to, total, breakdown);
    }

    private List<ReportDtos.Category> breakdown(UUID userId, LocalDate from, LocalDate to) {
        List<Object[]> rows = expenseRepository.aggregateByCategory(userId, from, to);

        BigDecimal total = rows.stream()
                .map(r -> (BigDecimal) r[3])
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return rows.stream()
                .map(r -> {
                    UUID categoryId = (UUID) r[0];
                    String name = (String) r[1];
                    String color = (String) r[2];
                    BigDecimal categoryTotal = (BigDecimal) r[3];
                    BigDecimal percentage = percentageOf(categoryTotal, total);
                    return new ReportDtos.Category(categoryId, name, color, categoryTotal, percentage);
                })
                .toList();
    }

    private static BigDecimal sumOf(List<ReportDtos.Category> breakdown) {
        return breakdown.stream()
                .map(ReportDtos.Category::total)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal percentageOf(BigDecimal part, BigDecimal total) {
        if (total.signum() == 0) return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        return part.multiply(BigDecimal.valueOf(100))
                .divide(total, 2, RoundingMode.HALF_UP);
    }
}

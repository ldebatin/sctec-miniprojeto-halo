package dev.halo.expense;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repositório do {@link Expense}. Estende {@link JpaSpecificationExecutor}
 * para suportar os filtros dinâmicos de {@code GET /expenses} (T-024) sem
 * precisar escrever JPQL com vários {@code WHERE} opcionais.
 */
public interface ExpenseRepository extends
        JpaRepository<Expense, UUID>,
        JpaSpecificationExecutor<Expense> {

    /**
     * Busca um gasto que ainda não foi soft-deleted e pertence ao usuário.
     * Tentativas de acessar gasto alheio caem aqui em {@link Optional#empty}
     * e o controller devolve 404 — sem vazar existência (T-024).
     */
    Optional<Expense> findByIdAndUserIdAndDeletedAtIsNull(UUID id, UUID userId);

    /**
     * Agregação por categoria para o {@code ReportService} (T-039). Devolve
     * tuplas {@code [categoryId, name, color, total]} ordenadas por total
     * desc; categorias sem gastos no período não aparecem.
     *
     * Faz JOIN explícito em {@code WHERE c.id = e.categoryId} porque
     * {@link Expense#getCategoryId()} não é uma associação JPA — é apenas
     * um {@code UUID}. Soft-deleted e gastos fora do range são filtrados.
     */
    @Query("""
            SELECT c.id, c.name, c.color, SUM(e.amount)
            FROM Expense e, dev.halo.category.Category c
            WHERE c.id = e.categoryId
              AND e.userId = :userId
              AND e.deletedAt IS NULL
              AND e.occurredAt BETWEEN :from AND :to
            GROUP BY c.id, c.name, c.color
            ORDER BY SUM(e.amount) DESC
            """)
    List<Object[]> aggregateByCategory(
            @Param("userId") UUID userId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    /** Lista de gastos do período (não soft-deleted) ordenada desc. */
    List<Expense> findByUserIdAndDeletedAtIsNullAndOccurredAtBetweenOrderByOccurredAtDescCreatedAtDesc(
            UUID userId, LocalDate from, LocalDate to);
}

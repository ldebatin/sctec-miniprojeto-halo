package dev.halo.expense;

import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/**
 * Filtros dinâmicos do {@code GET /expenses} (RF-13, T-024).
 *
 * Cada query param vira um {@link Specification} opcional; o controller
 * compõe via {@code .and(...)}. {@code deletedAt IS NULL} é sempre
 * aplicado para excluir registros soft-deleted (RF-14).
 */
final class ExpenseSpecifications {

    private ExpenseSpecifications() {}

    static Specification<Expense> notDeleted() {
        return (root, query, cb) -> cb.isNull(root.get("deletedAt"));
    }

    static Specification<Expense> ownedBy(UUID userId) {
        return (root, query, cb) -> cb.equal(root.get("userId"), userId);
    }

    /** Inclusivo nos dois extremos quando informados. */
    static Specification<Expense> between(LocalDate from, LocalDate to) {
        return (root, query, cb) -> {
            if (from == null && to == null) return cb.conjunction();
            if (from != null && to != null) return cb.between(root.get("occurredAt"), from, to);
            if (from != null) return cb.greaterThanOrEqualTo(root.get("occurredAt"), from);
            return cb.lessThanOrEqualTo(root.get("occurredAt"), to);
        };
    }

    static Specification<Expense> categoryEquals(UUID categoryId) {
        return (root, query, cb) ->
                categoryId == null ? cb.conjunction() : cb.equal(root.get("categoryId"), categoryId);
    }

    /** Match case-insensitive contendo o termo em {@code description}. */
    static Specification<Expense> descriptionContains(String q) {
        return (root, query, cb) -> {
            if (q == null || q.isBlank()) return cb.conjunction();
            String pattern = "%" + q.toLowerCase().trim() + "%";
            return cb.like(cb.lower(root.get("description")), pattern);
        };
    }
}

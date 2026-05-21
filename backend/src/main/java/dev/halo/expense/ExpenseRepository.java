package dev.halo.expense;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

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
}

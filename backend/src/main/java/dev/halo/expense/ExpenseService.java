package dev.halo.expense;

import dev.halo.ai.ExpenseParseResult;
import dev.halo.category.Category;
import dev.halo.category.CategoryGlobal;
import dev.halo.category.CategoryGlobalRepository;
import dev.halo.category.CategoryRepository;
import dev.halo.user.User;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persiste gastos vindos do WhatsApp e resolve a categoria a partir do
 * {@code category_hint} retornado pelo Gemini (RF-03, analise-tecnica.md §6.1).
 *
 * Ordem de match (T-014):
 * <ol>
 *   <li>Categoria customizada ativa do usuário com nome igual ao hint (ci);</li>
 *   <li>Categoria global com nome igual ao hint (ci) — uma cópia/override
 *       local é lazy-criada para satisfazer a FK {@code expenses.category_id};</li>
 *   <li>Fallback {@code "Sem categoria"} (seed na V3) — também lazy-copiado
 *       para o usuário na primeira utilização.</li>
 * </ol>
 *
 * Validação: {@code amount > 0}; valores nulos ou ≤ 0 são rejeitados com log
 * e o método devolve {@code null}.
 *
 * O fallback heurístico para quando o Gemini retorna {@code null} por erro
 * técnico (extrair valor por regex e cair em "Sem categoria") entra em T-016.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExpenseService {

    static final String UNCATEGORIZED_NAME = "Sem categoria";

    private final ExpenseRepository expenseRepository;
    private final CategoryRepository categoryRepository;
    private final CategoryGlobalRepository categoryGlobalRepository;

    /**
     * Persiste o gasto descrito por {@code parse} para o {@link User} informado.
     * Devolve {@code null} (sem persistir) quando o valor não é estritamente
     * positivo.
     */
    @Transactional
    public Expense createFromWhatsapp(User user, ExpenseParseResult parse, String rawMessage) {
        if (parse == null || parse.amount() == null
                || parse.amount().compareTo(BigDecimal.ZERO) <= 0) {
            BigDecimal amount = parse == null ? null : parse.amount();
            log.warn("Gasto rejeitado por valor não positivo userId={} amount={}",
                    user.getId(), amount);
            return null;
        }

        Category category = resolveCategoryForUser(user, parse.categoryHint());

        Instant now = Instant.now();
        Expense expense = new Expense();
        expense.setUserId(user.getId());
        expense.setCategoryId(category.getId());
        expense.setDescription(parse.description());
        expense.setAmount(parse.amount());
        expense.setOccurredAt(parse.occurredAt() != null ? parse.occurredAt() : LocalDate.now());
        expense.setSource(ExpenseSource.WHATSAPP);
        expense.setRawMessage(rawMessage);
        expense.setCreatedAt(now);
        expense.setUpdatedAt(now);

        return expenseRepository.save(expense);
    }

    /**
     * Visível pra teste e para o futuro {@code ExpenseService.createFromWeb} (T-024).
     */
    Category resolveCategoryForUser(User user, String hint) {
        if (hint != null && !hint.isBlank()) {
            Optional<Category> userCategory = categoryRepository
                    .findByUserIdAndActiveTrueAndNameIgnoreCase(user.getId(), hint.trim());
            if (userCategory.isPresent()) {
                return userCategory.get();
            }

            Optional<CategoryGlobal> globalMatch =
                    categoryGlobalRepository.findByNameIgnoreCase(hint.trim());
            if (globalMatch.isPresent()) {
                return getOrCreateUserCopyOf(user, globalMatch.get());
            }
        }

        CategoryGlobal uncategorized = categoryGlobalRepository
                .findByNameIgnoreCase(UNCATEGORIZED_NAME)
                .orElseThrow(() -> new IllegalStateException(
                        "Seed da categoria '" + UNCATEGORIZED_NAME + "' (V3) ausente"));
        return getOrCreateUserCopyOf(user, uncategorized);
    }

    /**
     * Garante que o usuário tenha uma {@link Category} ativa apontando para a
     * global informada (lazy-copy). FK {@code expenses.category_id} obriga uma
     * linha em {@code categories} (analise-tecnica.md §6.1, T-035 formaliza).
     */
    private Category getOrCreateUserCopyOf(User user, CategoryGlobal global) {
        return categoryRepository
                .findByUserIdAndGlobalIdAndActiveTrue(user.getId(), global.getId())
                .orElseGet(() -> createUserCopy(user, global));
    }

    private Category createUserCopy(User user, CategoryGlobal global) {
        Instant now = Instant.now();
        Category copy = new Category();
        copy.setUserId(user.getId());
        copy.setGlobalId(global.getId());
        copy.setName(global.getName());
        copy.setIcon(global.getIcon());
        copy.setColor(global.getColor());
        copy.setActive(true);
        copy.setCreatedAt(now);
        copy.setUpdatedAt(now);
        return categoryRepository.save(copy);
    }
}

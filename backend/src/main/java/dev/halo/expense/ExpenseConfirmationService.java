package dev.halo.expense;

import dev.halo.category.CategoryRepository;
import dev.halo.user.User;
import dev.halo.whatsapp.EvolutionClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Envia a confirmação textual de um gasto criado pelo {@link ExpenseService}
 * (RF-04, T-015). Busca o nome da categoria pelo {@code categoryId}, monta a
 * mensagem via {@link ExpenseConfirmationFormatter} e despacha através do
 * {@link EvolutionClient}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExpenseConfirmationService {

    private final CategoryRepository categoryRepository;
    private final EvolutionClient evolutionClient;

    @Transactional(readOnly = true)
    public void confirm(User user, Expense expense) {
        String categoryName = categoryRepository.findById(expense.getCategoryId())
                .map(c -> c.getName())
                .orElse(ExpenseService.UNCATEGORIZED_NAME);

        String message = ExpenseConfirmationFormatter.format(
                expense.getDescription(),
                expense.getAmount(),
                categoryName,
                expense.getOccurredAt());

        evolutionClient.sendText(user.getPhone(), message);
        log.info("Confirmação enviada userId={} expenseId={}", user.getId(), expense.getId());
    }
}

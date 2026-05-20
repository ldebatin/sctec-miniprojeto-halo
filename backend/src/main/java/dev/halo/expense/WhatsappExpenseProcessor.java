package dev.halo.expense;

import dev.halo.ai.ExpenseParseResult;
import dev.halo.category.CategoryGlobal;
import dev.halo.category.CategoryGlobalRepository;
import dev.halo.category.CategoryRepository;
import dev.halo.user.User;
import dev.halo.whatsapp.WhatsappMessage;
import dev.halo.whatsapp.WhatsappMessageStatus;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orquestra o fluxo end-to-end de um gasto vindo do WhatsApp (T-018):
 * Gemini/heurística → persistência → confirmação. Também atualiza o status
 * da {@link WhatsappMessage} correspondente.
 *
 * Resultados possíveis:
 * <ul>
 *   <li>{@code PROCESSED} — gasto persistido e confirmação enviada;</li>
 *   <li>{@code IGNORED} — parser entendeu como NOT_EXPENSE (sem valor);</li>
 *   <li>{@code FAILED} — valor inválido (≤ 0) recusado pelo {@link ExpenseService}.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WhatsappExpenseProcessor {

    private final WhatsappExpenseParser parser;
    private final ExpenseService expenseService;
    private final ExpenseConfirmationService confirmationService;
    private final CategoryRepository categoryRepository;
    private final CategoryGlobalRepository categoryGlobalRepository;

    @Transactional
    public void process(User user, WhatsappMessage message) {
        String content = message.getContent();
        if (content == null || content.isBlank()) {
            log.debug("Mensagem vazia — IGNORED msgId={}", message.getEvolutionMsgId());
            mark(message, WhatsappMessageStatus.IGNORED);
            return;
        }

        List<String> categoryNames = listCategoryNames(user);

        ExpenseParseResult parsed = parser.parse(content, categoryNames, user.getId());
        if (parsed == null) {
            log.debug("Parser não reconheceu gasto — IGNORED msgId={}", message.getEvolutionMsgId());
            mark(message, WhatsappMessageStatus.IGNORED);
            return;
        }

        Expense expense = expenseService.createFromWhatsapp(user, parsed, content);
        if (expense == null) {
            log.warn("ExpenseService recusou o registro — FAILED msgId={}", message.getEvolutionMsgId());
            mark(message, WhatsappMessageStatus.FAILED);
            return;
        }

        confirmationService.confirm(user, expense);
        mark(message, WhatsappMessageStatus.PROCESSED);
    }

    private List<String> listCategoryNames(User user) {
        Set<String> names = new LinkedHashSet<>();
        for (CategoryGlobal global : categoryGlobalRepository.findAll()) {
            names.add(global.getName());
        }
        categoryRepository.findByUserIdAndActiveTrue(user.getId())
                .forEach(c -> names.add(c.getName()));
        return List.copyOf(names);
    }

    private static void mark(WhatsappMessage message, WhatsappMessageStatus status) {
        message.setStatus(status);
        message.setProcessedAt(Instant.now());
    }
}

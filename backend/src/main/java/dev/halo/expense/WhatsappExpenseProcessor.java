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
 * Devolve o {@link WhatsappMessageStatus} resultante para que o caller
 * ({@code InboundMessageService}) possa despachar a mensagem de ajuda
 * quando a entrada não é reconhecível como gasto.
 *
 * Resultados possíveis:
 * <ul>
 *   <li>{@code PROCESSED} — gasto persistido e confirmação enviada;</li>
 *   <li>{@code IGNORED} — conteúdo nulo/vazio; não dá feedback ao usuário;</li>
 *   <li>{@code NOT_UNDERSTOOD} — parser não reconheceu como gasto; o caller
 *       deve enviar a mensagem de ajuda;</li>
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
    public WhatsappMessageStatus process(User user, WhatsappMessage message) {
        String content = message.getContent();
        if (content == null || content.isBlank()) {
            log.debug("Mensagem vazia — IGNORED msgId={}", message.getEvolutionMsgId());
            return mark(message, WhatsappMessageStatus.IGNORED);
        }

        List<String> categoryNames = listCategoryNames(user);

        ExpenseParseResult parsed = parser.parse(content, categoryNames, user.getId());
        if (parsed == null) {
            log.debug("Parser não reconheceu gasto — NOT_UNDERSTOOD msgId={}", message.getEvolutionMsgId());
            return mark(message, WhatsappMessageStatus.NOT_UNDERSTOOD);
        }

        Expense expense = expenseService.createFromWhatsapp(user, parsed, content);
        if (expense == null) {
            log.warn("ExpenseService recusou o registro — FAILED msgId={}", message.getEvolutionMsgId());
            return mark(message, WhatsappMessageStatus.FAILED);
        }

        confirmationService.confirm(user, expense);
        return mark(message, WhatsappMessageStatus.PROCESSED);
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

    private static WhatsappMessageStatus mark(WhatsappMessage message, WhatsappMessageStatus status) {
        message.setStatus(status);
        message.setProcessedAt(Instant.now());
        return status;
    }
}

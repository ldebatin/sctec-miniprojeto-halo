package dev.halo.expense;

import dev.halo.ai.ExpenseParseResult;
import dev.halo.ai.GeminiClient;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Orquestra o parsing de uma mensagem do WhatsApp em {@link ExpenseParseResult}.
 *
 * Tenta o {@link GeminiClient} primeiro. Se vier {@code null} (NOT_EXPENSE ou
 * erro técnico — o cliente atual não distingue os dois, ver T-013), aciona o
 * fallback heurístico do {@link AmountExtractor}:
 * <ul>
 *   <li>se um valor positivo é extraído → devolve {@link ExpenseParseResult}
 *       com {@code description=texto original}, {@code categoryHint=null}
 *       (cai para "Sem categoria" no {@link ExpenseService}) e
 *       {@code occurredAt=null} (default hoje na persistência);</li>
 *   <li>se nada de valor positivo é extraído → devolve {@code null} (mensagem
 *       ignorada).</li>
 * </ul>
 *
 * Log WARN registra cada acionamento do fallback — métrica útil pra observar
 * a saúde do Gemini.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WhatsappExpenseParser {

    private final GeminiClient geminiClient;

    public ExpenseParseResult parse(String text, List<String> userCategoryNames, UUID userId) {
        ExpenseParseResult fromGemini = geminiClient.parseExpense(text, userCategoryNames, userId);
        if (fromGemini != null) {
            return fromGemini;
        }

        BigDecimal amount = AmountExtractor.extract(text);
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            // Sem valor extraível → tratamos como NOT_EXPENSE (mensagem ignorada).
            return null;
        }

        log.warn("Fallback heurístico acionado (Gemini sem resposta) amount={} text='{}'",
                amount, text);
        return new ExpenseParseResult(
                text == null ? "" : text.trim(),
                amount,
                null, // categoryHint nulo → resolver usa "Sem categoria"
                null  // occurredAt nulo → service usa hoje
        );
    }
}

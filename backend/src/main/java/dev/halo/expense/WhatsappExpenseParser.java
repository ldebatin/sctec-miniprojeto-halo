package dev.halo.expense;

import dev.halo.ai.ClassificationCache;
import dev.halo.ai.ExpenseParseResult;
import dev.halo.ai.GeminiClient;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Orquestra o parsing de uma mensagem do WhatsApp em {@link ExpenseParseResult}.
 *
 * Ordem de tentativas:
 * <ol>
 *   <li><b>Cache de classificação (T-043)</b> — se o par
 *       {@code (userId, descrição normalizada)} já foi classificado antes,
 *       reusa o {@code categoryHint} cacheado e extrai o {@code amount} via
 *       {@link AmountExtractor}. Não chama o Gemini (zero token).</li>
 *   <li>{@link GeminiClient} — fonte primária de parse quando o cache não bate.
 *       Quando devolve {@code null} (NOT_EXPENSE ou erro técnico, ver T-013),
 *       cai no fallback heurístico.</li>
 *   <li>Fallback heurístico do {@link AmountExtractor}: se um valor positivo
 *       é extraído → devolve resultado com {@code categoryHint=null}; senão
 *       devolve {@code null} (mensagem ignorada).</li>
 * </ol>
 *
 * Log WARN registra cada acionamento do fallback — métrica útil pra observar
 * a saúde do Gemini.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WhatsappExpenseParser {

    private final GeminiClient geminiClient;
    private final ClassificationCache classificationCache;

    public ExpenseParseResult parse(String text, List<String> userCategoryNames, UUID userId) {
        Optional<String> cachedHint = classificationCache.getHint(userId, text);
        if (cachedHint.isPresent()) {
            BigDecimal amount = AmountExtractor.extract(text);
            if (amount != null && amount.compareTo(BigDecimal.ZERO) > 0) {
                log.debug("Classificação resolvida via cache userId={} hint={}",
                        userId, cachedHint.get());
                return new ExpenseParseResult(
                        text == null ? "" : text.trim(),
                        amount,
                        cachedHint.get(),
                        null);
            }
            // Cache tinha hint mas a mensagem atual não tem valor — segue pro
            // Gemini para garantir que decisões mais sutis (ex.: NOT_EXPENSE)
            // não sejam atropeladas pelo cache.
        }

        ExpenseParseResult fromGemini = geminiClient.parseExpense(text, userCategoryNames, userId);
        if (fromGemini != null) {
            classificationCache.putHint(userId, text, fromGemini.categoryHint());
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

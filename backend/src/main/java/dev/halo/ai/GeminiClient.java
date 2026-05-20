package dev.halo.ai;

import java.util.List;
import java.util.UUID;

/**
 * Cliente de IA para parsing de despesas (RF-03, analise-tecnica.md §9).
 */
public interface GeminiClient {

    /**
     * Pede ao Gemini para extrair os campos de uma despesa da mensagem do
     * usuário. Retorna {@code null} quando:
     * <ul>
     *   <li>a IA classifica a mensagem como {@code NOT_EXPENSE};</li>
     *   <li>a resposta não é um JSON válido no formato esperado;</li>
     *   <li>a chamada HTTP falha por qualquer motivo.</li>
     * </ul>
     *
     * Toda chamada — bem ou mal sucedida — é registrada em {@code ai_log} via
     * {@link AiLogService} (T-017), incluindo {@code userId} quando informado.
     *
     * @param text texto da mensagem do usuário (será truncado em 500 chars
     *             antes de enviar — §9.4)
     * @param userCategoryNames lista de categorias permitidas: globais +
     *                          customizadas ativas do usuário; usada na lista
     *                          de {@code category_hint} válidos do prompt
     * @param userId            usuário associado à chamada (pode ser
     *                          {@code null} no fluxo de cadastro, RF-01)
     */
    ExpenseParseResult parseExpense(String text, List<String> userCategoryNames, UUID userId);
}

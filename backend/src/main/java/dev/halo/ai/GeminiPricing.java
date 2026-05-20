package dev.halo.ai;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Preços (USD) do modelo {@code gemini-2.5-flash} usados para estimar o custo
 * em {@code ai_log.cost_est} (analise-tecnica.md §9.4).
 *
 * Fonte: https://ai.google.dev/pricing — valores são constantes (atualize-os
 * com PR dedicado quando o pricing mudar). T-017 só dá conta do flash; se
 * adicionarmos pro mais à frente, modele aqui.
 */
final class GeminiPricing {

    /** USD por milhão de tokens de entrada. */
    private static final BigDecimal INPUT_USD_PER_MILLION = new BigDecimal("0.075");

    /** USD por milhão de tokens de saída. */
    private static final BigDecimal OUTPUT_USD_PER_MILLION = new BigDecimal("0.30");

    private static final BigDecimal MILLION = BigDecimal.valueOf(1_000_000);

    private GeminiPricing() {}

    /**
     * Estima o custo da chamada em USD com base nos tokens informados pelo
     * {@code usageMetadata} do Gemini. Resultado arredondado para 6 casas
     * decimais (precisão do campo {@code ai_log.cost_est NUMERIC(10,6)}).
     */
    static BigDecimal estimate(int tokensIn, int tokensOut) {
        BigDecimal input = BigDecimal.valueOf(Math.max(0, tokensIn)).multiply(INPUT_USD_PER_MILLION);
        BigDecimal output = BigDecimal.valueOf(Math.max(0, tokensOut)).multiply(OUTPUT_USD_PER_MILLION);
        return input.add(output).divide(MILLION, 6, RoundingMode.HALF_UP);
    }
}

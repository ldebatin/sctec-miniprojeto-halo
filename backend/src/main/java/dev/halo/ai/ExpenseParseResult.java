package dev.halo.ai;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Resultado do parser de gastos do Gemini (RF-03, analise-tecnica.md §9.2).
 *
 * <ul>
 *   <li>{@code description} — descrição curta extraída da mensagem.</li>
 *   <li>{@code amount} — valor em reais como {@link BigDecimal} ({@code numeric(12,2)}
 *       no banco). Nunca {@code float}/{@code double} — ver CLAUDE.md §Conventions.</li>
 *   <li>{@code categoryHint} — nome da categoria sugerida pela IA; deve bater
 *       com alguma global ou customizada do usuário (T-014 resolve a categoria
 *       de fato).</li>
 *   <li>{@code occurredAt} — data do gasto. {@code null} quando a mensagem não
 *       menciona data — o serviço de persistência usa hoje como default (RF-03).</li>
 * </ul>
 */
public record ExpenseParseResult(
        String description,
        BigDecimal amount,
        String categoryHint,
        LocalDate occurredAt
) {}

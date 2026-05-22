package dev.halo.expense;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.halo.ai.ClassificationCache;
import dev.halo.ai.ExpenseParseResult;
import dev.halo.ai.GeminiClient;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Cobre os 4 critérios da T-016 — fallback heurístico quando Gemini não responde.
 */
class WhatsappExpenseParserTest {

    private GeminiClient geminiClient;
    private ClassificationCache classificationCache;
    private WhatsappExpenseParser parser;

    @BeforeEach
    void setUp() {
        geminiClient = mock(GeminiClient.class);
        classificationCache = mock(ClassificationCache.class);
        // Default: cache miss
        when(classificationCache.getHint(any(), any())).thenReturn(Optional.empty());
        parser = new WhatsappExpenseParser(geminiClient, classificationCache);
    }

    @Test
    void usa_resultado_do_Gemini_quando_disponivel() {
        ExpenseParseResult expected = new ExpenseParseResult(
                "Mercado", new BigDecimal("87.30"), "Mercado", LocalDate.of(2026, 5, 18));
        when(geminiClient.parseExpense(anyString(), anyList(), any())).thenReturn(expected);

        ExpenseParseResult result = parser.parse("Mercado 87,30", List.of("Mercado"), null);

        assertThat(result).isSameAs(expected);
    }

    @Test
    void fallback_persiste_gasto_quando_Gemini_falha_e_regex_acha_valor() {
        when(geminiClient.parseExpense(anyString(), anyList(), any())).thenReturn(null);

        ExpenseParseResult result = parser.parse("Mercado 87,30", List.of("Mercado"), null);

        assertThat(result).isNotNull();
        assertThat(result.amount()).isEqualByComparingTo(new BigDecimal("87.30"));
        assertThat(result.description()).isEqualTo("Mercado 87,30");
        // categoryHint null → ExpenseService cai em "Sem categoria"
        assertThat(result.categoryHint()).isNull();
        // occurredAt null → ExpenseService usa hoje
        assertThat(result.occurredAt()).isNull();
    }

    @Test
    void ignora_mensagem_quando_Gemini_falha_e_regex_nao_acha_valor() {
        when(geminiClient.parseExpense(anyString(), anyList(), any())).thenReturn(null);

        ExpenseParseResult result = parser.parse("Oi, tudo bem?", List.of("Mercado"), null);

        assertThat(result).isNull();
    }

    @Test
    void ignora_mensagem_com_valor_zero_extraido() {
        when(geminiClient.parseExpense(anyString(), anyList(), any())).thenReturn(null);

        ExpenseParseResult result = parser.parse("estou na conta 0", List.of("Mercado"), null);

        assertThat(result).isNull();
    }

    @Test
    void fallback_lida_com_valor_pt_BR_com_milhar() {
        when(geminiClient.parseExpense(any(), any(), any())).thenReturn(null);

        ExpenseParseResult result = parser.parse("Notebook 1.234,56", List.of(), null);

        assertThat(result).isNotNull();
        assertThat(result.amount()).isEqualByComparingTo(new BigDecimal("1234.56"));
    }

    // ----------------------------------------------------------------
    // Cache de classificação (T-043)
    // ----------------------------------------------------------------

    @Test
    void cache_hit_resolve_sem_chamar_o_gemini() {
        UUID userId = UUID.randomUUID();
        when(classificationCache.getHint(eq(userId), any())).thenReturn(Optional.of("Mercado"));

        ExpenseParseResult result = parser.parse("Mercado 87,30", List.of("Mercado"), userId);

        assertThat(result).isNotNull();
        assertThat(result.categoryHint()).isEqualTo("Mercado");
        assertThat(result.amount()).isEqualByComparingTo(new BigDecimal("87.30"));
        // Gemini não é chamado
        verify(geminiClient, never()).parseExpense(any(), any(), any());
    }

    @Test
    void cache_miss_chama_gemini_e_popula_o_cache_no_sucesso() {
        UUID userId = UUID.randomUUID();
        ExpenseParseResult expected = new ExpenseParseResult(
                "Mercado", new BigDecimal("87.30"), "Mercado", LocalDate.of(2026, 5, 18));
        when(geminiClient.parseExpense(anyString(), anyList(), any())).thenReturn(expected);

        ExpenseParseResult result = parser.parse("Mercado 87,30", List.of("Mercado"), userId);

        assertThat(result).isSameAs(expected);
        verify(classificationCache).putHint(eq(userId), eq("Mercado 87,30"), eq("Mercado"));
    }

    @Test
    void cache_hit_sem_amount_no_texto_cai_pro_gemini() {
        UUID userId = UUID.randomUUID();
        when(classificationCache.getHint(eq(userId), any())).thenReturn(Optional.of("Lazer"));
        // Texto sem valor — cache não consegue resolver sozinho
        ExpenseParseResult fromGemini = new ExpenseParseResult(
                "Ida ao cinema", new BigDecimal("40"), "Lazer", LocalDate.of(2026, 5, 18));
        when(geminiClient.parseExpense(anyString(), anyList(), any())).thenReturn(fromGemini);

        ExpenseParseResult result = parser.parse("Cinema com a Carla", List.of("Lazer"), userId);

        assertThat(result).isSameAs(fromGemini);
        verify(geminiClient).parseExpense(any(), any(), any());
    }

    @Test
    void cache_nao_eh_populado_quando_gemini_devolve_categoryHint_null() {
        UUID userId = UUID.randomUUID();
        when(geminiClient.parseExpense(any(), any(), any())).thenReturn(null);

        // Texto com valor: fallback heurístico devolve resultado mas com hint=null
        parser.parse("alguma compra 10", List.of(), userId);

        verify(classificationCache, never()).putHint(any(), any(), any());
    }
}

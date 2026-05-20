package dev.halo.expense;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.halo.ai.ExpenseParseResult;
import dev.halo.ai.GeminiClient;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Cobre os 4 critérios da T-016 — fallback heurístico quando Gemini não responde.
 */
class WhatsappExpenseParserTest {

    private GeminiClient geminiClient;
    private WhatsappExpenseParser parser;

    @BeforeEach
    void setUp() {
        geminiClient = mock(GeminiClient.class);
        parser = new WhatsappExpenseParser(geminiClient);
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
}

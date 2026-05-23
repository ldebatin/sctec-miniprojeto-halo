package dev.halo.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Testes puros de funções estáticas — truncamento de input (§9.4) e
 * montagem do prompt (§9.2).
 */
class HttpGeminiClientUnitTest {

    @Test
    void truncate_corta_em_500_chars() {
        String longInput = "x".repeat(800);
        assertThat(HttpGeminiClient.truncate(longInput)).hasSize(500);
    }

    @Test
    void truncate_devolve_string_curta_intacta() {
        assertThat(HttpGeminiClient.truncate("Mercado 87,30")).isEqualTo("Mercado 87,30");
    }

    @Test
    void truncate_devolve_vazio_para_nulo() {
        assertThat(HttpGeminiClient.truncate(null)).isEmpty();
    }

    @Test
    void prompt_inclui_lista_de_categorias_e_texto_do_usuario() {
        String prompt = HttpGeminiClient.buildPrompt(
                "Mercado 87,30",
                List.of("Alimentação", "Mercado", "Transporte"),
                LocalDate.of(2026, 5, 22));

        assertThat(prompt).contains("Alimentação, Mercado, Transporte");
        assertThat(prompt).contains("Mercado 87,30");
        assertThat(prompt).contains("\"description\"");
        assertThat(prompt).contains("\"amount\"");
        assertThat(prompt).contains("\"category_hint\"");
        assertThat(prompt).contains("\"occurred_at\"");
        assertThat(prompt).contains("NOT_EXPENSE");
    }

    @Test
    void prompt_com_lista_vazia_marca_nenhuma() {
        String prompt = HttpGeminiClient.buildPrompt(
                "Uber 25", List.of(), LocalDate.of(2026, 5, 22));
        assertThat(prompt).contains("(nenhuma)");
    }

    @Test
    void prompt_inclui_data_atual_e_instrucoes_para_datas_relativas() {
        // Sem âncora de "hoje" o modelo chutava datas (sintoma observado:
        // "uber 33 ontem" virava 2025-01-01). A âncora + as instruções
        // explícitas de ontem/anteontem precisam estar no prompt.
        String prompt = HttpGeminiClient.buildPrompt(
                "uber 33 ontem", List.of("Transporte"), LocalDate.of(2026, 5, 22));

        assertThat(prompt).contains("2026-05-22");
        assertThat(prompt).contains("ontem");
        assertThat(prompt).contains("NÃO invente datas");
    }

    @Test
    void redactNumbers_mascara_valores_inteiros_e_decimais() {
        String raw = "{\"description\":\"Mercado\",\"amount\":87.30,\"occurred_at\":\"2026-05-18\"}";
        String masked = HttpGeminiClient.redactNumbers(raw);
        assertThat(masked)
                .contains("\"description\":\"Mercado\"")
                .doesNotContain("87.30")
                .doesNotContain("2026")
                .contains("<num>");
    }

    @Test
    void redactNumbers_trunca_em_RAW_LOG_LIMIT() {
        String raw = "abc".repeat(200); // 600 chars
        String masked = HttpGeminiClient.redactNumbers(raw);
        // RAW_LOG_LIMIT chars + reticências
        assertThat(masked).hasSize(HttpGeminiClient.RAW_LOG_LIMIT + 3);
        assertThat(masked).endsWith("...");
    }

    @Test
    void redactNumbers_devolve_marcador_para_nulo() {
        assertThat(HttpGeminiClient.redactNumbers(null)).isEqualTo("<null>");
    }
}

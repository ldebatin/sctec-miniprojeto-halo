package dev.halo.ai;

import static org.assertj.core.api.Assertions.assertThat;

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
                List.of("Alimentação", "Mercado", "Transporte"));

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
        String prompt = HttpGeminiClient.buildPrompt("Uber 25", List.of());
        assertThat(prompt).contains("(nenhuma)");
    }
}

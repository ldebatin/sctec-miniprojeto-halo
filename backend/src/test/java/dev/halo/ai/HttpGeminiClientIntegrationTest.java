package dev.halo.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integração do {@link HttpGeminiClient} contra um {@link MockRestServiceServer}.
 * Valida parsing OK, NOT_EXPENSE → null, JSON inválido → null, erro HTTP → null,
 * truncamento (§9.4) e parâmetros do request (§9.2 — temperature, max_output_tokens,
 * response_mime_type, prompt contendo categorias).
 */
@SpringBootTest
@Testcontainers
@TestPropertySource(properties = {
        "halo.gemini.base-url=http://gemini.test",
        "halo.gemini.api-key=fake-key",
        "halo.gemini.model=gemini-2.5-flash"
})
class HttpGeminiClientIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    private static final String ENDPOINT =
            "http://gemini.test/v1beta/models/gemini-2.5-flash:generateContent?key=fake-key";

    @TestConfiguration
    static class TestConfig {

        @Bean
        @Primary
        RestClient.Builder testRestClientBuilder() {
            return RestClient.builder();
        }

        @Bean
        MockRestServiceServer mockServer(RestClient.Builder builder) {
            return MockRestServiceServer.bindTo(builder).build();
        }

        @Bean
        @Primary
        HttpGeminiClient httpGeminiClient(
                GeminiProperties properties,
                RestClient.Builder builder,
                MockRestServiceServer mockServer,
                AiLogService aiLogService) {
            return new HttpGeminiClient(properties, builder, aiLogService);
        }
    }

    @Autowired
    private HttpGeminiClient client;

    @Autowired
    private MockRestServiceServer mockServer;

    @BeforeEach
    void resetMock() {
        mockServer.reset();
    }

    private static String successResponse(String inlineJson) {
        // O modelo devolve o JSON do gasto dentro do campo text do part.
        // Escapa aspas para encaixar no JSON externo.
        String escaped = inlineJson.replace("\"", "\\\"");
        return """
                {
                  "candidates": [
                    {
                      "content": {
                        "parts": [{ "text": "%s" }]
                      }
                    }
                  ]
                }
                """.formatted(escaped);
    }

    @Test
    void parse_ok_devolve_resultado_e_envia_request_correto() {
        String modelJson = """
                {"description":"Mercado","amount":87.30,"category_hint":"Mercado","occurred_at":"2026-05-18"}""";

        mockServer.expect(requestTo(ENDPOINT))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.generationConfig.temperature").value(0.2))
                .andExpect(jsonPath("$.generationConfig.maxOutputTokens").value(200))
                .andExpect(jsonPath("$.generationConfig.responseMimeType").value("application/json"))
                .andExpect(jsonPath("$.contents[0].parts[0].text",
                        Matchers.containsString("Alimentação, Mercado")))
                .andExpect(jsonPath("$.contents[0].parts[0].text",
                        Matchers.containsString("Mercado 87,30")))
                .andRespond(withSuccess(successResponse(modelJson), MediaType.APPLICATION_JSON));

        ExpenseParseResult result = client.parseExpense(
                "Mercado 87,30", List.of("Alimentação", "Mercado"), null);

        assertThat(result).isNotNull();
        assertThat(result.description()).isEqualTo("Mercado");
        assertThat(result.amount()).isEqualByComparingTo(new BigDecimal("87.30"));
        assertThat(result.categoryHint()).isEqualTo("Mercado");
        assertThat(result.occurredAt()).isEqualTo(LocalDate.of(2026, 5, 18));
        mockServer.verify();
    }

    @Test
    void NOT_EXPENSE_devolve_null() {
        mockServer.expect(requestTo(ENDPOINT))
                .andRespond(withSuccess(
                        successResponse("{\"error\":\"NOT_EXPENSE\"}"),
                        MediaType.APPLICATION_JSON));

        ExpenseParseResult result = client.parseExpense("oi", List.of("Mercado"), null);

        assertThat(result).isNull();
        mockServer.verify();
    }

    @Test
    void json_invalido_devolve_null() {
        mockServer.expect(requestTo(ENDPOINT))
                .andRespond(withSuccess(
                        successResponse("isto-nao-eh-json"),
                        MediaType.APPLICATION_JSON));

        ExpenseParseResult result = client.parseExpense("Mercado 87,30", List.of("Mercado"), null);

        assertThat(result).isNull();
        mockServer.verify();
    }

    @Test
    void erro_http_devolve_null() {
        mockServer.expect(requestTo(ENDPOINT))
                .andRespond(withServerError());

        ExpenseParseResult result = client.parseExpense("Mercado 87,30", List.of("Mercado"), null);

        assertThat(result).isNull();
        mockServer.verify();
    }

    @Test
    void mensagem_longa_e_truncada_antes_de_enviar() {
        String longUserText = "x".repeat(800);
        String modelJson =
                "{\"description\":\"x\",\"amount\":1.00,\"category_hint\":\"Outros\",\"occurred_at\":null}";

        mockServer.expect(requestTo(ENDPOINT))
                // Garante que o prompt enviado NÃO contém os últimos 'x's após o 500º char:
                // o texto do prompt termina com '"""\n' depois do truncado, nunca com 800 'x'.
                .andExpect(content().string(Matchers.not(Matchers.containsString("x".repeat(501)))))
                .andExpect(content().string(Matchers.containsString("x".repeat(500))))
                .andRespond(withSuccess(successResponse(modelJson), MediaType.APPLICATION_JSON));

        ExpenseParseResult result = client.parseExpense(longUserText, List.of("Outros"), null);

        assertThat(result).isNotNull();
        mockServer.verify();
    }

    @Test
    void data_ausente_e_normalizada_para_null() {
        String modelJson =
                "{\"description\":\"Uber\",\"amount\":25.00,\"category_hint\":\"Transporte\",\"occurred_at\":null}";

        mockServer.expect(requestTo(ENDPOINT))
                .andRespond(withSuccess(successResponse(modelJson), MediaType.APPLICATION_JSON));

        ExpenseParseResult result = client.parseExpense("Uber 25", List.of("Transporte"), null);

        assertThat(result).isNotNull();
        assertThat(result.occurredAt()).isNull();
    }
}

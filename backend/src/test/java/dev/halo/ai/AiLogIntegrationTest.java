package dev.halo.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import dev.halo.user.User;
import dev.halo.user.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Cobre os 4 critérios da T-017 — toda chamada ao Gemini grava uma linha
 * em {@code ai_log} com {@code user_id}, hash SHA-256 do prompt, tokens e
 * status apropriado para sucesso, JSON inválido e erro HTTP.
 */
@SpringBootTest
@Testcontainers
@TestPropertySource(properties = {
        "halo.gemini.base-url=http://gemini.test",
        "halo.gemini.api-key=fake-key",
        "halo.gemini.model=gemini-2.5-flash"
})
class AiLogIntegrationTest {

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

    @Autowired
    private AiLogRepository aiLogRepository;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void reset() {
        mockServer.reset();
        aiLogRepository.deleteAll();
        userRepository.deleteAll();
    }

    private UUID insertUser() {
        User user = new User();
        user.setPhone("+5547999999999");
        user.setName("Maria");
        Instant now = Instant.now();
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        return userRepository.save(user).getId();
    }

    private static String successResponse(String inlineJson, int tokensIn, int tokensOut) {
        String escaped = inlineJson.replace("\"", "\\\"");
        return """
                {
                  "candidates": [
                    { "content": { "parts": [{ "text": "%s" }] } }
                  ],
                  "usageMetadata": {
                    "promptTokenCount": %d,
                    "candidatesTokenCount": %d
                  }
                }
                """.formatted(escaped, tokensIn, tokensOut);
    }

    @Test
    void chamada_OK_registra_user_id_tokens_e_custo() {
        UUID userId = insertUser();
        String modelJson =
                "{\"description\":\"Mercado\",\"amount\":87.30,\"category_hint\":\"Mercado\",\"occurred_at\":null}";

        mockServer.expect(requestTo(ENDPOINT))
                .andRespond(withSuccess(successResponse(modelJson, 120, 30),
                        MediaType.APPLICATION_JSON));

        ExpenseParseResult result = client.parseExpense("Mercado 87,30", List.of("Mercado"), userId);

        assertThat(result).isNotNull();
        List<AiLog> rows = aiLogRepository.findAll();
        assertThat(rows).hasSize(1);
        AiLog log = rows.get(0);
        assertThat(log.getUserId()).isEqualTo(userId);
        assertThat(log.getModel()).isEqualTo("gemini-2.5-flash");
        assertThat(log.getPromptHash()).hasSize(64).matches("[0-9a-f]+");
        assertThat(log.getTokensIn()).isEqualTo(120);
        assertThat(log.getTokensOut()).isEqualTo(30);
        assertThat(log.getStatus()).isEqualTo(AiLogStatus.OK);
        // 120*0.075/1M + 30*0.30/1M = 0.000009 + 0.000009 = 0.000018
        assertThat(log.getCostEst()).isEqualByComparingTo(new BigDecimal("0.000018"));
    }

    @Test
    void chamada_com_JSON_invalido_registra_INVALID_JSON() {
        mockServer.expect(requestTo(ENDPOINT))
                .andRespond(withSuccess(successResponse("isto-nao-eh-json", 50, 10),
                        MediaType.APPLICATION_JSON));

        ExpenseParseResult result = client.parseExpense("Mercado 87,30", List.of("Mercado"), null);

        assertThat(result).isNull();
        List<AiLog> rows = aiLogRepository.findAll();
        assertThat(rows).hasSize(1);
        AiLog log = rows.get(0);
        assertThat(log.getUserId()).isNull();
        assertThat(log.getStatus()).isEqualTo(AiLogStatus.INVALID_JSON);
        assertThat(log.getTokensIn()).isEqualTo(50);
        assertThat(log.getTokensOut()).isEqualTo(10);
    }

    @Test
    void erro_HTTP_registra_ERROR_com_tokens_zero_e_custo_zero() {
        UUID userId = insertUser();
        mockServer.expect(requestTo(ENDPOINT)).andRespond(withServerError());

        ExpenseParseResult result = client.parseExpense("Mercado 87,30", List.of("Mercado"), userId);

        assertThat(result).isNull();
        List<AiLog> rows = aiLogRepository.findAll();
        assertThat(rows).hasSize(1);
        AiLog log = rows.get(0);
        assertThat(log.getUserId()).isEqualTo(userId);
        assertThat(log.getStatus()).isEqualTo(AiLogStatus.ERROR);
        assertThat(log.getTokensIn()).isZero();
        assertThat(log.getTokensOut()).isZero();
        assertThat(log.getCostEst()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void NOT_EXPENSE_registra_OK_porque_a_classificacao_e_valida() {
        mockServer.expect(requestTo(ENDPOINT))
                .andRespond(withSuccess(
                        successResponse("{\"error\":\"NOT_EXPENSE\"}", 40, 5),
                        MediaType.APPLICATION_JSON));

        ExpenseParseResult result = client.parseExpense("Oi", List.of(), null);

        assertThat(result).isNull();
        List<AiLog> rows = aiLogRepository.findAll();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getStatus()).isEqualTo(AiLogStatus.OK);
    }
}

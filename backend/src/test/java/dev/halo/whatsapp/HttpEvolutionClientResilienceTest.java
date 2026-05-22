package dev.halo.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import dev.halo.whatsapp.config.EvolutionProperties;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
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
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Cobre o comportamento do {@link HttpEvolutionClient} sob falhas (RF-04).
 *
 * <p>Substitui o {@link RestClient} interno por um amarrado a um
 * {@link MockRestServiceServer} via {@link TestConfig}. A configuração de
 * {@code resilience4j} é mantida (o AOP roda), só baixamos o tempo de espera
 * para o teste ser rápido.
 */
@SpringBootTest
@Testcontainers
@TestPropertySource(properties = {
        // backoff curtinho para os testes não rodarem em segundos
        "resilience4j.retry.instances.evolution.wait-duration=10ms",
        "resilience4j.retry.instances.evolution.exponential-backoff-multiplier=1",
        "halo.evolution.base-url=http://evolution.test",
        "halo.evolution.instance=halo-bot",
        "halo.evolution.instance-token=fake-token",
        "halo.evolution.api-key=fake-apikey"
})
class HttpEvolutionClientResilienceTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @TestConfiguration
    static class TestConfig {

        @Bean
        @Primary
        RestClient.Builder testRestClientBuilder() {
            return RestClient.builder();
        }

        @Bean
        MockRestServiceServer mockServer(RestClient.Builder builder) {
            // bindTo modifica o builder para usar um request factory de mock.
            return MockRestServiceServer.bindTo(builder).build();
        }

        // Substitui o bean auto-discovered para garantir que o HttpEvolutionClient
        // só seja construído APÓS o MockRestServiceServer.bindTo já ter rodado
        // sobre o RestClient.Builder. Sem essa dependência explícita o
        // HttpEvolutionClient pode ser criado antes do bind e o RestClient
        // interno fica apontando para a URL real.
        @Bean
        @Primary
        HttpEvolutionClient httpEvolutionClient(
                EvolutionProperties properties,
                RestClient.Builder builder,
                MockRestServiceServer mockServer) {
            return new HttpEvolutionClient(properties, builder);
        }
    }

    @Autowired
    private HttpEvolutionClient client;

    @Autowired
    private MockRestServiceServer mockServer;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @BeforeEach
    void resetCircuitBreaker() {
        circuitBreakerRegistry.circuitBreaker("evolution").reset();
        mockServer.reset();
    }

    @Test
    void sendText_chama_endpoint_correto_no_caminho_feliz() {
        mockServer.expect(requestTo("http://evolution.test/send/text"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers
                        .header("instance", "halo-bot"))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers
                        .header("apikey", "fake-token"))
                .andRespond(withSuccess());

        client.sendText("+5547999999999", "Oi");

        mockServer.verify();
    }

    @Test
    void retry_em_5xx_ate_obter_sucesso() {
        mockServer.expect(requestTo("http://evolution.test/send/text"))
                .andRespond(withServerError());
        mockServer.expect(requestTo("http://evolution.test/send/text"))
                .andRespond(withServerError());
        mockServer.expect(requestTo("http://evolution.test/send/text"))
                .andRespond(withSuccess());

        client.sendText("+5547999999999", "Oi");

        mockServer.verify();
    }

    @Test
    void erros_4xx_nao_disparam_retry() {
        mockServer.expect(requestTo("http://evolution.test/send/text"))
                .andRespond(withBadRequest());

        assertThatThrownBy(() -> client.sendText("+5547999999999", "Oi"))
                .isInstanceOf(HttpClientErrorException.class);

        mockServer.verify();
    }

    // ----------------------------------------------------------------
    // sendMedia (T-042) — caminho feliz, body, 50KB, retry, CB
    // ----------------------------------------------------------------

    @Test
    void sendMedia_chama_endpoint_correto_com_body_base64() {
        byte[] png = new byte[]{(byte) 0x89, 'P', 'N', 'G', 1, 2, 3};
        String expectedBase64 = java.util.Base64.getEncoder().encodeToString(png);
        String expectedDataUri = "data:image/png;base64," + expectedBase64;

        mockServer.expect(requestTo("http://evolution.test/send/media"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers
                        .header("instance", "halo-bot"))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers
                        .header("apikey", "fake-token"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.number").value("5547999999999"))
                .andExpect(jsonPath("$.type").value("image"))
                .andExpect(jsonPath("$.url").value(expectedDataUri))
                .andExpect(jsonPath("$.filename").value("halo-summary.png"))
                .andExpect(jsonPath("$.caption").value("Resumo de Maio"))
                .andRespond(withSuccess());

        client.sendMedia("+5547999999999", png, "image/png", "Resumo de Maio");

        mockServer.verify();
    }

    @Test
    void sendMedia_aceita_caption_null() {
        byte[] png = new byte[]{1, 2, 3};
        mockServer.expect(requestTo("http://evolution.test/send/media"))
                .andExpect(jsonPath("$.caption").doesNotExist())
                .andRespond(withSuccess());

        client.sendMedia("+5547999999999", png, "image/png", null);

        mockServer.verify();
    }

    @Test
    void sendMedia_com_imagem_de_50KB_funciona() {
        byte[] big = new byte[50 * 1024];
        for (int i = 0; i < big.length; i++) big[i] = (byte) (i % 256);

        mockServer.expect(requestTo("http://evolution.test/send/media"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess());

        client.sendMedia("+5547999999999", big, "image/png", "50KB");

        mockServer.verify();
    }

    @Test
    void sendMedia_imageBytes_vazio_lanca_imediatamente_sem_chamar_o_server() {
        assertThatThrownBy(() -> client.sendMedia("+5547999999999", new byte[0], "image/png", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> client.sendMedia("+5547999999999", null, "image/png", null))
                .isInstanceOf(IllegalArgumentException.class);

        mockServer.verify(); // nenhuma expectativa registrada
    }

    @Test
    void sendMedia_retry_em_5xx_ate_obter_sucesso() {
        byte[] png = new byte[]{1, 2, 3};
        mockServer.expect(requestTo("http://evolution.test/send/media"))
                .andRespond(withServerError());
        mockServer.expect(requestTo("http://evolution.test/send/media"))
                .andRespond(withServerError());
        mockServer.expect(requestTo("http://evolution.test/send/media"))
                .andRespond(withSuccess());

        client.sendMedia("+5547999999999", png, "image/png", null);

        mockServer.verify();
    }

    @Test
    void circuit_breaker_abre_apos_5_falhas_consecutivas() {
        // 5 chamadas, cada uma retornando 503 nas 3 tentativas do retry = 15 respostas
        for (int i = 0; i < 15; i++) {
            mockServer.expect(requestTo("http://evolution.test/send/text"))
                    .andRespond(withServerError());
        }

        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> client.sendText("+5547999999999", "tentativa"))
                    .as("Falha esperada na chamada %d", i)
                    .isInstanceOf(org.springframework.web.client.HttpServerErrorException.class);
        }

        // 6ª chamada não deve nem bater no servidor — CB aberto
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("evolution");
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThatThrownBy(() -> client.sendText("+5547999999999", "bloqueada"))
                .isInstanceOf(CallNotPermittedException.class);

        mockServer.verify();
    }
}

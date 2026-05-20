package dev.halo.whatsapp;

import dev.halo.whatsapp.config.EvolutionProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Implementação HTTP do {@link EvolutionClient} via Spring {@link RestClient}.
 *
 * Aponta para {@code POST /message/sendText/{instance}} usando o
 * {@code instance-token} (header {@code apikey}) — ver CLAUDE.md §"Evolution Go
 * — modelo de auth em 2 níveis".
 *
 * Esta task (T-011) entrega só o caminho feliz (sem retry, sem circuit breaker
 * e sem timeouts customizados); T-012 vai adicionar retry exponencial e
 * Resilience4j.
 */
@Component
@Slf4j
public class HttpEvolutionClient implements EvolutionClient {

    private final EvolutionProperties properties;
    private final RestClient restClient;

    public HttpEvolutionClient(EvolutionProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .defaultHeader("apikey", properties.instanceToken())
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public void sendText(String phoneE164, String text) {
        // Evolution espera o número sem o "+" no campo "number".
        String number = phoneE164 != null && phoneE164.startsWith("+")
                ? phoneE164.substring(1)
                : phoneE164;

        SendTextRequest body = new SendTextRequest(number, text);

        restClient.post()
                .uri("/message/sendText/{instance}", properties.instance())
                .body(body)
                .retrieve()
                .toBodilessEntity();

        log.info("Mensagem enviada via Evolution instance={} phone={}",
                properties.instance(), phoneE164);
    }

    /** Payload do {@code POST /message/sendText/{instance}}. */
    private record SendTextRequest(String number, String text) {}
}

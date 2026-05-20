package dev.halo.whatsapp;

import dev.halo.whatsapp.config.EvolutionProperties;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
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
 * <p>T-011 entregou o caminho feliz. T-012 adicionou as anotações
 * Resilience4j ({@link Retry} + {@link CircuitBreaker}) — configuração da
 * instância {@code evolution} fica em {@code application.yml} (3 tentativas
 * 1s/3s/9s em 5xx ou IOException; CB abre após 5 falhas consecutivas, meio-
 * aberto após 30s; 4xx falha imediatamente sem contar para o CB).
 */
@Component
@Slf4j
public class HttpEvolutionClient implements EvolutionClient {

    static final String RESILIENCE_INSTANCE = "evolution";

    private final EvolutionProperties properties;
    private final RestClient restClient;

    public HttpEvolutionClient(EvolutionProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        // Evolution Go: o nome da instância vai no header `instance` (não no path).
        // A versão Node usaria `/message/sendText/{instance}` — ver
        // docs/setup-evolution.md §2.B. Auth para endpoints por-instância
        // (incluindo /send/text) é o INSTANCE TOKEN, não a global api-key
        // (CLAUDE.md §"Evolution Go — modelo de auth em 2 níveis", confirmado
        // empiricamente no bugfix da T-011/T-012).
        this.restClient = builder
                .baseUrl(properties.baseUrl())
                .defaultHeader("apikey", properties.instanceToken())
                .defaultHeader("instance", properties.instance())
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    @Retry(name = RESILIENCE_INSTANCE)
    @CircuitBreaker(name = RESILIENCE_INSTANCE)
    public void sendText(String phoneE164, String text) {
        // Evolution espera o número sem o "+" no campo "number".
        String number = phoneE164 != null && phoneE164.startsWith("+")
                ? phoneE164.substring(1)
                : phoneE164;

        SendTextRequest body = new SendTextRequest(number, text);

        restClient.post()
                .uri("/send/text")
                .body(body)
                .retrieve()
                .toBodilessEntity();

        log.info("Mensagem enviada via Evolution instance={} phone={}",
                properties.instance(), phoneE164);
    }

    /** Payload do {@code POST /message/sendText/{instance}}. */
    private record SendTextRequest(String number, String text) {}
}

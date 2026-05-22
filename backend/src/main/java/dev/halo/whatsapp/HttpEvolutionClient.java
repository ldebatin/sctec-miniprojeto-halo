package dev.halo.whatsapp;

import dev.halo.whatsapp.config.EvolutionProperties;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.util.Base64;
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
        //
        // A checagem do instanceToken acontece em sendText(), não aqui — caso
        // contrário qualquer @SpringBootTest sem a property setada quebra ao
        // instanciar o context, mesmo sem ir chamar /send/text.
        this.restClient = builder
                .baseUrl(properties.baseUrl())
                .defaultHeader("apikey", properties.instanceToken() == null ? "" : properties.instanceToken())
                .defaultHeader("instance", properties.instance() == null ? "" : properties.instance())
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    @Retry(name = RESILIENCE_INSTANCE)
    @CircuitBreaker(name = RESILIENCE_INSTANCE)
    public void sendText(String phoneE164, String text) {
        if (properties.instanceToken() == null || properties.instanceToken().isBlank()) {
            // Fail-fast no envio (não na construção): sem este token, /send/text
            // vira 401. Foi a situação que pegamos com o IntelliJ rodando o
            // backend com working dir na raiz do repo (spring-dotenv não
            // encontrava backend/.env) — ver CLAUDE.md §"Local dev stack".
            throw new IllegalStateException(
                    "EVOLUTION_INSTANCE_TOKEN não configurado — confira backend/.env "
                            + "e o working directory do processo (deve ser backend/).");
        }
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

    @Override
    @Retry(name = RESILIENCE_INSTANCE)
    @CircuitBreaker(name = RESILIENCE_INSTANCE)
    public void sendMedia(String phoneE164, byte[] imageBytes, String mimeType, String caption) {
        if (properties.instanceToken() == null || properties.instanceToken().isBlank()) {
            throw new IllegalStateException(
                    "EVOLUTION_INSTANCE_TOKEN não configurado — confira backend/.env "
                            + "e o working directory do processo (deve ser backend/).");
        }
        if (imageBytes == null || imageBytes.length == 0) {
            throw new IllegalArgumentException("imageBytes vazio");
        }

        String number = phoneE164 != null && phoneE164.startsWith("+")
                ? phoneE164.substring(1)
                : phoneE164;
        // Evolution Go espera no campo `url` ou uma URL HTTP, ou base64 puro
        // (SEM o prefixo `data:<mime>;base64,`). Com data URI ele responde
        // 400 "invalid base64 encoding" porque tenta decodificar a string
        // inteira incluindo o prefixo. Validado por curl direto no swagger
        // local em /swagger/doc.json — schema MediaStruct.
        String base64 = Base64.getEncoder().encodeToString(imageBytes);

        SendMediaRequest body = new SendMediaRequest(
                number,
                "image",
                base64,
                caption == null || caption.isBlank() ? null : caption,
                "halo-summary.png");

        restClient.post()
                .uri("/send/media")
                .body(body)
                .retrieve()
                .toBodilessEntity();

        log.info("Mídia enviada via Evolution instance={} phone={} bytes={}",
                properties.instance(), phoneE164, imageBytes.length);
    }

    /** Payload do {@code POST /message/sendText/{instance}}. */
    private record SendTextRequest(String number, String text) {}

    /**
     * Payload do {@code POST /send/media} (Evolution Go — schema
     * {@code MediaStruct}). Campos relevantes:
     *
     * <ul>
     *   <li>{@code type} — "image", "video", "audio", "document".</li>
     *   <li>{@code url} — URL HTTP **ou** base64 cru. Data URI
     *       ({@code data:<mime>;base64,...}) NÃO funciona: Evolution Go
     *       tenta decodificar a string inteira como base64, incluindo o
     *       prefixo, e devolve 400 "invalid base64 encoding".</li>
     *   <li>{@code caption} — texto opcional.</li>
     *   <li>{@code filename} — nome lógico exibido em alguns clientes.</li>
     * </ul>
     */
    private record SendMediaRequest(
            String number,
            String type,
            String url,
            String caption,
            String filename
    ) {}
}

package dev.halo.ai;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Implementação HTTP do {@link GeminiClient} contra a API REST do Google AI
 * (endpoint {@code generativelanguage.googleapis.com}).
 *
 * Esta task (T-013) só faz: montar o prompt do §9.2, truncar mensagens > 500
 * chars (§9.4), chamar {@code generateContent} com {@code temperature=0.2},
 * {@code maxOutputTokens=200} e {@code responseMimeType=application/json}, e
 * devolver {@link ExpenseParseResult} ou {@code null} (NOT_EXPENSE / JSON
 * inválido / erro HTTP).
 *
 * <p>Logging em {@code ai_log} (tokens, latência, custo) entra em T-017.
 * Cache de classificação por descrição entra em T-043.
 */
@Component
@Slf4j
public class HttpGeminiClient implements GeminiClient {

    /** §9.4 — limita o tamanho do prompt para controlar custo. */
    static final int MAX_INPUT_CHARS = 500;

    static final double TEMPERATURE = 0.2;
    static final int MAX_OUTPUT_TOKENS = 200;

    private final GeminiProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public HttpGeminiClient(GeminiProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        this.restClient = builder
                .baseUrl(properties.baseUrl())
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Override
    public ExpenseParseResult parseExpense(String text, List<String> userCategoryNames) {
        String prompt = buildPrompt(truncate(text), userCategoryNames);
        GenerateContentRequest body = new GenerateContentRequest(
                List.of(new Content(List.of(new Part(prompt)))),
                new GenerationConfig(TEMPERATURE, MAX_OUTPUT_TOKENS, "application/json")
        );

        GenerateContentResponse response;
        try {
            response = restClient.post()
                    .uri("/v1beta/models/{model}:generateContent?key={apiKey}",
                            properties.model(), properties.apiKey())
                    .body(body)
                    .retrieve()
                    .body(GenerateContentResponse.class);
        } catch (RestClientException e) {
            log.warn("Falha na chamada ao Gemini: {}", e.getMessage());
            return null;
        }

        String rawJson = extractText(response);
        if (rawJson == null || rawJson.isBlank()) {
            log.warn("Resposta do Gemini sem conteúdo");
            return null;
        }

        return parseJson(rawJson);
    }

    /** §9.4 — trunca mensagens muito longas antes de enviar. */
    static String truncate(String text) {
        if (text == null) return "";
        return text.length() > MAX_INPUT_CHARS ? text.substring(0, MAX_INPUT_CHARS) : text;
    }

    /** §9.2 — template do prompt. Lista de categorias é injetada como CSV. */
    static String buildPrompt(String userText, List<String> categories) {
        String categoryList = categories == null || categories.isEmpty()
                ? "(nenhuma)"
                : String.join(", ", categories);
        return """
                Você é um parser de despesas. Receba a mensagem do usuário e devolva APENAS
                JSON válido no formato:
                {
                  "description": string,
                  "amount": number,
                  "category_hint": string,
                  "occurred_at": "YYYY-MM-DD" | null
                }
                Regras:
                - Se a mensagem não parece descrever um gasto, devolva {"error":"NOT_EXPENSE"}.
                - Se a data não foi informada, deixe null.
                - Categorias válidas: %s.
                Mensagem: \"\"\"%s\"\"\"
                """.formatted(categoryList, userText);
    }

    private ExpenseParseResult parseJson(String raw) {
        try {
            ExpenseJson dto = objectMapper.readValue(raw, ExpenseJson.class);
            if (dto.error() != null) {
                log.debug("Gemini classificou como não-gasto: {}", dto.error());
                return null;
            }
            if (dto.description() == null || dto.amount() == null) {
                log.warn("JSON do Gemini incompleto: {}", raw);
                return null;
            }
            LocalDate occurredAt = parseDate(dto.occurredAt());
            return new ExpenseParseResult(
                    dto.description(),
                    dto.amount(),
                    dto.categoryHint(),
                    occurredAt
            );
        } catch (Exception e) {
            log.warn("JSON do Gemini inválido: {}", e.getMessage());
            return null;
        }
    }

    private static LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank() || "null".equalsIgnoreCase(raw)) {
            return null;
        }
        try {
            return LocalDate.parse(raw);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String extractText(GenerateContentResponse response) {
        if (response == null || response.candidates() == null || response.candidates().isEmpty()) {
            return null;
        }
        Candidate first = response.candidates().get(0);
        if (first.content() == null || first.content().parts() == null
                || first.content().parts().isEmpty()) {
            return null;
        }
        return first.content().parts().get(0).text();
    }

    // ---------- DTOs da API do Gemini ----------

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record GenerateContentRequest(List<Content> contents, GenerationConfig generationConfig) {}

    private record Content(List<Part> parts) {}

    private record Part(String text) {}

    private record GenerationConfig(
            double temperature,
            int maxOutputTokens,
            String responseMimeType
    ) {}

    private record GenerateContentResponse(List<Candidate> candidates) {}

    private record Candidate(Content content) {}

    /** Estrutura esperada do JSON devolvido pelo modelo (corpo do {@code Part.text}). */
    private record ExpenseJson(
            String description,
            BigDecimal amount,
            @JsonProperty("category_hint") String categoryHint,
            @JsonProperty("occurred_at") String occurredAt,
            String error
    ) {}
}

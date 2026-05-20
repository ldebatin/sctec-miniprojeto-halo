package dev.halo.ai;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Implementação HTTP do {@link GeminiClient} contra a API REST do Google AI
 * (endpoint {@code generativelanguage.googleapis.com}).
 *
 * Esta task (T-017) registra cada chamada em {@code ai_log} via
 * {@link AiLogService} — sucesso ({@code OK}), JSON inválido
 * ({@code INVALID_JSON}) ou erro de rede ({@code ERROR}). Tokens vêm do
 * {@code usageMetadata} da resposta; quando não há resposta (erro HTTP), os
 * contadores ficam em zero.
 *
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
    private final AiLogService aiLogService;

    public HttpGeminiClient(
            GeminiProperties properties,
            RestClient.Builder builder,
            AiLogService aiLogService) {
        this.properties = properties;
        this.restClient = builder
                .baseUrl(properties.baseUrl())
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.aiLogService = aiLogService;
    }

    @Override
    public ExpenseParseResult parseExpense(
            String text, List<String> userCategoryNames, UUID userId) {
        String prompt = buildPrompt(truncate(text), userCategoryNames);
        GenerateContentRequest body = new GenerateContentRequest(
                List.of(new Content(List.of(new Part(prompt)))),
                new GenerationConfig(TEMPERATURE, MAX_OUTPUT_TOKENS, "application/json")
        );

        long startedAt = System.currentTimeMillis();
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
            aiLogService.record(userId, properties.model(), prompt, 0, 0,
                    elapsedMs(startedAt), AiLogStatus.ERROR);
            return null;
        }

        int tokensIn = tokensIn(response);
        int tokensOut = tokensOut(response);
        int latencyMs = elapsedMs(startedAt);

        String rawJson = extractText(response);
        if (rawJson == null || rawJson.isBlank()) {
            log.warn("Resposta do Gemini sem conteúdo");
            aiLogService.record(userId, properties.model(), prompt, tokensIn, tokensOut,
                    latencyMs, AiLogStatus.INVALID_JSON);
            return null;
        }

        ParseOutcome outcome = parseJson(rawJson);
        aiLogService.record(userId, properties.model(), prompt, tokensIn, tokensOut,
                latencyMs, outcome.status());
        return outcome.result();
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

    private ParseOutcome parseJson(String raw) {
        try {
            ExpenseJson dto = objectMapper.readValue(raw, ExpenseJson.class);
            if (dto.error() != null) {
                // NOT_EXPENSE é classificação correta — log como OK, conteúdo válido.
                log.debug("Gemini classificou como não-gasto: {}", dto.error());
                return new ParseOutcome(AiLogStatus.OK, null);
            }
            if (dto.description() == null || dto.amount() == null) {
                log.warn("JSON do Gemini incompleto: {}", raw);
                return new ParseOutcome(AiLogStatus.INVALID_JSON, null);
            }
            LocalDate occurredAt = parseDate(dto.occurredAt());
            return new ParseOutcome(AiLogStatus.OK, new ExpenseParseResult(
                    dto.description(),
                    dto.amount(),
                    dto.categoryHint(),
                    occurredAt
            ));
        } catch (Exception e) {
            log.warn("JSON do Gemini inválido: {}", e.getMessage());
            return new ParseOutcome(AiLogStatus.INVALID_JSON, null);
        }
    }

    private static int elapsedMs(long startedAt) {
        return (int) Math.min(Integer.MAX_VALUE, System.currentTimeMillis() - startedAt);
    }

    private static int tokensIn(GenerateContentResponse response) {
        if (response == null || response.usageMetadata() == null) return 0;
        return response.usageMetadata().promptTokenCount();
    }

    private static int tokensOut(GenerateContentResponse response) {
        if (response == null || response.usageMetadata() == null) return 0;
        return response.usageMetadata().candidatesTokenCount();
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

    private record GenerateContentResponse(
            List<Candidate> candidates,
            UsageMetadata usageMetadata
    ) {}

    private record Candidate(Content content) {}

    private record UsageMetadata(
            int promptTokenCount,
            int candidatesTokenCount
    ) {}

    /** Estrutura esperada do JSON devolvido pelo modelo (corpo do {@code Part.text}). */
    private record ExpenseJson(
            String description,
            BigDecimal amount,
            @JsonProperty("category_hint") String categoryHint,
            @JsonProperty("occurred_at") String occurredAt,
            String error
    ) {}

    /** Resultado interno do {@code parseJson} — combina status para o ai_log e o ParseResult. */
    private record ParseOutcome(AiLogStatus status, ExpenseParseResult result) {}
}

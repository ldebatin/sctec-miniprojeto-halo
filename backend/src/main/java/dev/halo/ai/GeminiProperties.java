package dev.halo.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propriedades do Gemini usadas pelo {@link GeminiClient} (analise-tecnica.md §9).
 *
 * <ul>
 *   <li>{@code apiKey} — chave da API do Google AI Studio (envia como
 *       query param {@code ?key=...}).</li>
 *   <li>{@code baseUrl} — root da API. Default produção:
 *       {@code https://generativelanguage.googleapis.com}. Em testes pode ser
 *       substituído por um mock server.</li>
 *   <li>{@code model} — nome do modelo. Default {@code gemini-2.5-flash} (§9.1).</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "halo.gemini")
public record GeminiProperties(
        String apiKey,
        String baseUrl,
        String model
) {}

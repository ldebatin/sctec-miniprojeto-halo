package dev.halo.whatsapp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propriedades do Evolution Go usadas pelo backend (analise-tecnica.md §8).
 *
 * <ul>
 *   <li>{@code apiKey} — segredo compartilhado para validar o webhook que CHEGA
 *       no backend (mesmo valor que {@code EVOLUTION_API_KEY} no
 *       {@code infra/.env}). Usado pelo {@code EvolutionWebhookController}.</li>
 *   <li>{@code baseUrl} — URL base do Evolution Go (ex.: {@code http://localhost:8081}).
 *       Usado pelo {@code EvolutionClient} ao SAIR para o Evolution.</li>
 *   <li>{@code instance} — nome da instância criada via Manager UI ({@code halo-bot}).</li>
 *   <li>{@code instanceToken} — token retornado por {@code POST /instance/create}
 *       (ver CLAUDE.md §"Evolution Go — modelo de auth em 2 níveis"). Necessário
 *       para os endpoints POR INSTÂNCIA como {@code /message/sendText/{instance}}.</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "halo.evolution")
public record EvolutionProperties(
        String apiKey,
        String baseUrl,
        String instance,
        String instanceToken
) {}

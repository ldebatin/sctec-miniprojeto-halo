package dev.halo.whatsapp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propriedades do Evolution Go usadas pelo backend (analise-tecnica.md §8).
 *
 * <ul>
 *   <li>{@code apiKey} — GLOBAL_API_KEY do Evolution Go. Usado pelo backend nas
 *       chamadas de SAÍDA aos endpoints globais ({@code /instance/all},
 *       {@code /instance/create} etc.). Mesmo valor que {@code EVOLUTION_API_KEY}
 *       no {@code infra/.env}.</li>
 *   <li>{@code baseUrl} — URL base do Evolution Go (ex.: {@code http://localhost:8081}).
 *       Usado pelo {@code EvolutionClient} ao SAIR para o Evolution.</li>
 *   <li>{@code instance} — nome da instância criada via Manager UI ({@code halo-bot}).</li>
 *   <li>{@code instanceToken} — token retornado por {@code POST /instance/create}
 *       (ver CLAUDE.md §"Evolution Go — modelo de auth em 2 níveis"). Necessário
 *       para os endpoints POR INSTÂNCIA como {@code /message/sendText/{instance}}.</li>
 * </ul>
 *
 * O webhook que CHEGA no backend ({@code POST /webhooks/evolution}) não tem
 * propriedade de auth aqui — o Evolution Go self-hosted não envia auth em
 * webhooks de saída (issue upstream #1933 closed as not-planned); a proteção
 * fica na rede / reverse proxy à frente do backend.
 */
@ConfigurationProperties(prefix = "halo.evolution")
public record EvolutionProperties(
        String apiKey,
        String baseUrl,
        String instance,
        String instanceToken
) {}

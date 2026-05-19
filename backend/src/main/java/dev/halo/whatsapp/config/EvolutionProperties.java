package dev.halo.whatsapp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "halo.evolution")
public record EvolutionProperties(
        String apiKey
) {}

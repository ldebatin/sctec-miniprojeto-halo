package dev.halo.web;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * URL pública do PWA e gatilhos do comando de link via WhatsApp (RF-10).
 */
@ConfigurationProperties(prefix = "halo.web")
public record WebProperties(
        String publicUrl,
        List<String> linkTriggers
) {}

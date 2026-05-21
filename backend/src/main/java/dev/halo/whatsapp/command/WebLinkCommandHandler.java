package dev.halo.whatsapp.command;

import dev.halo.web.WebProperties;
import dev.halo.whatsapp.EvolutionClient;
import dev.halo.whatsapp.WhatsappMessage;
import dev.halo.whatsapp.WhatsappMessageStatus;
import java.time.Instant;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Comando "site/link/web" no fluxo de mensagens WhatsApp (RF-10, analise-tecnica.md §7).
 *
 * Esta task (T-037) só faz: reconhecer gatilhos configuráveis (case-insensitive),
 * responder com {@code halo.web.public-url} via {@link EvolutionClient} e marcar a
 * mensagem como {@code PROCESSED} sem chamar o parser de gasto / Gemini.
 * Comandos "resumo" e variantes entram em T-038.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WebLinkCommandHandler {

    static final String MESSAGE_TEMPLATE =
            "Acesse o Halo pela web:\n\n%s";

    private final WebProperties webProperties;
    private final EvolutionClient evolutionClient;

    /**
     * Trata a mensagem se for um gatilho de link da web.
     *
     * @return {@code true} se o comando foi reconhecido e a resposta foi enviada
     */
    public boolean tryHandle(String phoneE164, String content, WhatsappMessage message) {
        if (content == null || content.isBlank()) {
            return false;
        }
        if (!isLinkTrigger(content)) {
            return false;
        }

        String publicUrl = webProperties.publicUrl();
        if (publicUrl == null || publicUrl.isBlank()) {
            log.warn("halo.web.public-url não configurada — comando de link ignorado phone={}",
                    phoneE164);
            return false;
        }

        String reply = MESSAGE_TEMPLATE.formatted(publicUrl.trim());
        evolutionClient.sendText(phoneE164, reply);
        markProcessed(message);
        log.info("Comando de link web atendido phone={} msgId={}", phoneE164, message.getEvolutionMsgId());
        return true;
    }

    boolean isLinkTrigger(String content) {
        String trimmed = content.trim();
        if (trimmed.isEmpty()) {
            return false;
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);
        return webProperties.linkTriggers().stream()
                .anyMatch(trigger -> trigger != null
                        && trigger.toLowerCase(Locale.ROOT).equals(lower));
    }

    private static void markProcessed(WhatsappMessage message) {
        message.setStatus(WhatsappMessageStatus.PROCESSED);
        message.setProcessedAt(Instant.now());
    }
}

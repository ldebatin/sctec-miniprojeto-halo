package dev.halo.whatsapp;

import dev.halo.whatsapp.config.EvolutionProperties;
import dev.halo.whatsapp.dto.EvolutionPayloadDto;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Webhook que recebe eventos do Evolution Go (RF-02, analise-tecnica.md §8.2).
 *
 * Esta task (T-008) só faz: validar apikey, ignorar fromMe e devolver 200.
 * A persistência idempotente em whatsapp_messages, a resolução de usuário
 * por telefone e o disparo do parser de gasto entram em T-009 / T-010 / T-013.
 */
@RestController
@RequestMapping("/webhooks/evolution")
@RequiredArgsConstructor
@Slf4j
public class EvolutionWebhookController {

    private final EvolutionProperties properties;

    @PostMapping
    public ResponseEntity<Void> receive(
            @RequestHeader(value = "apikey", required = false) String apikey,
            @RequestBody EvolutionPayloadDto payload
    ) {
        if (!isAuthorized(apikey)) {
            log.warn("Webhook recusado: apikey inválido ou ausente");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        if (isFromMe(payload)) {
            log.debug("Webhook ignorado (fromMe=true) msgId={}", msgId(payload));
            return ResponseEntity.ok().build();
        }

        log.info("Webhook recebido: event={} instance={} msgId={} pushName={}",
                payload.event(), payload.instance(), msgId(payload),
                payload.data() != null ? payload.data().pushName() : null);

        return ResponseEntity.ok().build();
    }

    private boolean isAuthorized(String received) {
        String expected = properties.apiKey();
        if (expected == null || expected.isBlank() || received == null) {
            return false;
        }
        return MessageDigest.isEqual(
                received.getBytes(StandardCharsets.UTF_8),
                expected.getBytes(StandardCharsets.UTF_8));
    }

    private boolean isFromMe(EvolutionPayloadDto payload) {
        return payload != null
                && payload.data() != null
                && payload.data().key() != null
                && Boolean.TRUE.equals(payload.data().key().fromMe());
    }

    private String msgId(EvolutionPayloadDto payload) {
        if (payload == null || payload.data() == null || payload.data().key() == null) {
            return null;
        }
        return payload.data().key().id();
    }
}

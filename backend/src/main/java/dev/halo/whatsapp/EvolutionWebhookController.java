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
 * Esta task (T-010) faz: validar apikey, ignorar fromMe, persistir a mensagem
 * em {@code whatsapp_messages} via {@link InboundMessageService} (idempotente)
 * e popular {@code user_id} quando o telefone bate com um usuário cadastrado.
 * Sempre devolve 200 nos eventos aceitos.
 *
 * O disparo do parser de gasto entra em T-013.
 */
@RestController
@RequestMapping("/webhooks/evolution")
@RequiredArgsConstructor
@Slf4j
public class EvolutionWebhookController {

    private final EvolutionProperties properties;
    private final InboundMessageService inboundMessageService;

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

        if (!hasMsgId(payload)) {
            log.warn("Webhook sem msgId; nada a persistir event={} instance={}",
                    payload != null ? payload.event() : null,
                    payload != null ? payload.instance() : null);
            return ResponseEntity.ok().build();
        }

        inboundMessageService.record(payload);

        log.info("Webhook processado event={} instance={} msgId={} pushName={}",
                payload.event(), payload.instance(), msgId(payload),
                payload.data().pushName());

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

    private boolean hasMsgId(EvolutionPayloadDto payload) {
        return msgId(payload) != null && !msgId(payload).isBlank();
    }

    private String msgId(EvolutionPayloadDto payload) {
        if (payload == null || payload.data() == null || payload.data().key() == null) {
            return null;
        }
        return payload.data().key().id();
    }
}

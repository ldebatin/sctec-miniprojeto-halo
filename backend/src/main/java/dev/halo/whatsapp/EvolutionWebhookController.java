package dev.halo.whatsapp;

import dev.halo.whatsapp.dto.EvolutionGoWebhookPayload;
import dev.halo.whatsapp.dto.EvolutionPayloadDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Webhook que recebe eventos do Evolution Go (RF-01/RF-02, analise-tecnica.md §8.2).
 *
 * Sem autenticação no endpoint — o Evolution Go self-hosted não envia auth em
 * webhooks de saída (issue upstream #1933 closed as not-planned). A proteção
 * fica a cargo da rede / reverse proxy à frente do backend (ver §10.3 do
 * doc técnico).
 *
 * O wire format do Evolution Go usa envelope Baileys (PascalCase) e difere do
 * Evolution API Node v2 originalmente documentado. {@link EvolutionGoWebhookPayload}
 * modela o wire e {@code toCanonical()} converte para o DTO interno
 * {@link EvolutionPayloadDto} consumido pelo service.
 *
 * Esta task (T-011) faz: ignorar fromMe, persistir a mensagem em
 * {@code whatsapp_messages} via {@link InboundMessageService} (idempotente
 * + resolução de usuário + cadastro conversacional AWAITING_NAME). Sempre
 * devolve 200 nos eventos aceitos.
 *
 * O disparo do parser de gasto entra em T-013.
 */
@RestController
@RequestMapping("/webhooks/evolution")
@RequiredArgsConstructor
@Slf4j
public class EvolutionWebhookController {

    private final InboundMessageService inboundMessageService;

    @PostMapping
    public ResponseEntity<Void> receive(@RequestBody EvolutionGoWebhookPayload wire) {
        EvolutionPayloadDto payload = wire.toCanonical();

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

        try {
            inboundMessageService.record(payload);
            log.info("Webhook processado event={} instance={} msgId={} pushName={}",
                    payload.event(), payload.instance(), msgId(payload),
                    payload.data().pushName());
        } catch (DuplicateWebhookException e) {
            // Evolution Go entrega o mesmo evento 2x; já foi processado por
            // outra thread. Devolver 200 para o Evolution não tentar de novo.
            log.info("Webhook duplicado ignorado msgId={}", e.getMsgId());
        }

        return ResponseEntity.ok().build();
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

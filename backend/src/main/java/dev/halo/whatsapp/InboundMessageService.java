package dev.halo.whatsapp;

import dev.halo.whatsapp.dto.EvolutionPayloadDto;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persiste mensagens recebidas via webhook do Evolution Go em
 * {@code whatsapp_messages} de forma idempotente (RF-01, analise-tecnica.md §6.2).
 *
 * Esta task (T-009) só faz: gravar a linha com {@code direction=IN},
 * {@code status=RECEIVED} e {@code evolution_msg_id} único; e expor uma
 * normalização inline do JID para E.164 que será extraída em
 * {@code PhoneNumberService} (T-010).
 *
 * Lookup de usuário por telefone e atualização de {@code user_id} entram em T-010.
 * Disparo do parser de gasto e atualização de {@code status=PROCESSED}/{@code FAILED}
 * entram em T-013.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InboundMessageService {

    private static final String WHATSAPP_JID_SUFFIX = "@s.whatsapp.net";

    private final WhatsappMessageRepository repository;

    /**
     * Persiste a mensagem se ainda não existe; caso contrário devolve a existente.
     *
     * Idempotência é garantida por {@code evolution_msg_id UNIQUE} no banco — o
     * pre-check via {@code findByEvolutionMsgId} resolve o caso comum sem
     * depender da exception do banco.
     */
    @Transactional
    public WhatsappMessage record(EvolutionPayloadDto payload) {
        EvolutionPayloadDto.Data data = payload.data();
        String msgId = data.key().id();

        Optional<WhatsappMessage> existing = repository.findByEvolutionMsgId(msgId);
        if (existing.isPresent()) {
            log.debug("Mensagem já registrada (idempotência) msgId={}", msgId);
            return existing.get();
        }

        String phone = normalizeJidToE164(data.key().remoteJid());

        WhatsappMessage message = new WhatsappMessage();
        message.setEvolutionMsgId(msgId);
        message.setDirection(WhatsappDirection.IN);
        message.setStatus(WhatsappMessageStatus.RECEIVED);
        message.setContent(data.message() != null ? data.message().conversation() : null);
        message.setReceivedAt(Instant.now());
        // userId permanece nulo — T-010 fará o lookup pelo phone normalizado.

        WhatsappMessage saved = repository.save(message);
        log.info("Mensagem registrada msgId={} phone={} pushName={}",
                msgId, phone, data.pushName());
        return saved;
    }

    /**
     * Normaliza um {@code remoteJid} do Evolution para o formato E.164 ({@code +<DDI><número>}).
     *
     * Exemplos:
     * <ul>
     *   <li>{@code 5547999999999@s.whatsapp.net} → {@code +5547999999999}</li>
     *   <li>{@code 5547999999999} → {@code +5547999999999}</li>
     *   <li>{@code +5547999999999} → {@code +5547999999999}</li>
     * </ul>
     *
     * Versão definitiva — com validação rigorosa e exceção tratada — entra em
     * {@code PhoneNumberService} na T-010.
     */
    static String normalizeJidToE164(String remoteJid) {
        if (remoteJid == null || remoteJid.isBlank()) {
            return null;
        }
        String trimmed = remoteJid.trim();
        int suffixAt = trimmed.indexOf(WHATSAPP_JID_SUFFIX);
        if (suffixAt > 0) {
            trimmed = trimmed.substring(0, suffixAt);
        }
        if (trimmed.startsWith("+")) {
            return trimmed;
        }
        return "+" + trimmed;
    }
}

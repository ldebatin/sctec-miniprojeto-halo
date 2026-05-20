package dev.halo.whatsapp;

import dev.halo.user.InvalidPhoneException;
import dev.halo.user.PhoneNumberService;
import dev.halo.user.User;
import dev.halo.user.UserService;
import dev.halo.whatsapp.dto.EvolutionPayloadDto;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persiste mensagens recebidas via webhook do Evolution Go em
 * {@code whatsapp_messages} de forma idempotente (RF-01/RF-02,
 * analise-tecnica.md §6.2).
 *
 * Esta task (T-010) adiciona: normalizar o {@code remoteJid} para E.164 via
 * {@link PhoneNumberService} e popular {@code user_id} quando o telefone bate
 * com um {@link User} já cadastrado (via {@link UserService#findOrNull(String)}).
 * Telefone inválido vira log de warning e a mensagem é persistida com
 * {@code user_id=null} — o webhook não pode quebrar.
 *
 * Cadastro conversacional (criar usuário em {@code AWAITING_NAME}) entra em
 * T-011. Disparo do parser de gasto e atualização de {@code status=PROCESSED}/
 * {@code FAILED} entram em T-013.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InboundMessageService {

    private final WhatsappMessageRepository repository;
    private final PhoneNumberService phoneNumberService;
    private final UserService userService;

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

        WhatsappMessage message = new WhatsappMessage();
        message.setEvolutionMsgId(msgId);
        message.setDirection(WhatsappDirection.IN);
        message.setStatus(WhatsappMessageStatus.RECEIVED);
        message.setContent(data.message() != null ? data.message().conversation() : null);
        message.setReceivedAt(Instant.now());

        String rawJid = data.key().remoteJid();
        try {
            String phone = phoneNumberService.normalize(rawJid);
            User user = userService.findOrNull(phone);
            if (user != null) {
                message.setUserId(user.getId());
            }
            log.info("Mensagem registrada msgId={} phone={} userResolved={} pushName={}",
                    msgId, phone, user != null, data.pushName());
        } catch (InvalidPhoneException e) {
            // Webhook é a fronteira do sistema: registra o evento mas não propaga —
            // Evolution não deve receber 5xx por payload defeituoso.
            log.warn("Telefone inválido no payload msgId={} jid={} erro={}",
                    msgId, rawJid, e.getMessage());
        }

        return repository.save(message);
    }
}

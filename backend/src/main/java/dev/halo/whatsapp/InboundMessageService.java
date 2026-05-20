package dev.halo.whatsapp;

import dev.halo.user.InvalidPhoneException;
import dev.halo.user.PhoneNumberService;
import dev.halo.user.User;
import dev.halo.user.UserService;
import dev.halo.whatsapp.conversation.ConversationService;
import dev.halo.whatsapp.dto.EvolutionPayloadDto;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persiste mensagens recebidas via webhook do Evolution Go em
 * {@code whatsapp_messages} de forma idempotente e dispara o roteamento
 * conversacional inicial (RF-01/RF-02, analise-tecnica.md §6.2/§7.1).
 *
 * Esta task (T-011) adiciona: quando o telefone normaliza mas não bate com um
 * {@link User} existente, delega para {@link ConversationService} (cadastro
 * AWAITING_NAME). Telefone inválido continua sendo logado e persistido com
 * {@code user_id=null} para não quebrar o webhook.
 *
 * Disparo do parser de gasto (para usuários já cadastrados) e atualização de
 * {@code status=PROCESSED}/{@code FAILED} entram em T-013.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InboundMessageService {

    private final WhatsappMessageRepository repository;
    private final PhoneNumberService phoneNumberService;
    private final UserService userService;
    private final ConversationService conversationService;

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
        String content = data.message() != null ? data.message().conversation() : null;
        message.setContent(content);
        message.setReceivedAt(Instant.now());

        String rawJid = data.key().remoteJid();
        String normalizedPhone = null;
        try {
            normalizedPhone = phoneNumberService.normalize(rawJid);
            User user = userService.findOrNull(normalizedPhone);
            if (user != null) {
                message.setUserId(user.getId());
            }
            log.info("Mensagem registrada msgId={} phone={} userResolved={} pushName={}",
                    msgId, normalizedPhone, user != null, data.pushName());
        } catch (InvalidPhoneException e) {
            // Webhook é a fronteira do sistema: registra o evento mas não propaga —
            // Evolution não deve receber 5xx por payload defeituoso.
            log.warn("Telefone inválido no payload msgId={} jid={} erro={}",
                    msgId, rawJid, e.getMessage());
        }

        WhatsappMessage saved = repository.save(message);

        // Cadastro conversacional só roda para telefone normalizado SEM usuário
        // cadastrado. Usuários existentes vão para o parser de gasto na T-013.
        if (normalizedPhone != null && saved.getUserId() == null) {
            conversationService.handleAwaitingName(normalizedPhone, content);
        }

        return saved;
    }
}

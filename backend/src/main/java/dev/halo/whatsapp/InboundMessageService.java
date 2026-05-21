package dev.halo.whatsapp;

import dev.halo.expense.WhatsappExpenseProcessor;
import dev.halo.user.InvalidPhoneException;
import dev.halo.user.PhoneNumberService;
import dev.halo.user.User;
import dev.halo.user.UserService;
import dev.halo.whatsapp.command.WebLinkCommandHandler;
import dev.halo.whatsapp.conversation.ConversationService;
import dev.halo.whatsapp.dto.EvolutionPayloadDto;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persiste mensagens recebidas via webhook do Evolution Go em
 * {@code whatsapp_messages} de forma idempotente e dispara o roteamento
 * conversacional inicial (RF-01/RF-02/RF-03, analise-tecnica.md §6.2/§7.1/§7.2).
 *
 * Esta task (T-018) fecha o fluxo end-to-end da Release 1:
 * <ul>
 *   <li>Telefone inválido → log warning, mensagem persistida com {@code user_id=null}.</li>
 *   <li>Telefone válido sem cadastro → delega para {@link ConversationService}
 *       (AWAITING_NAME, T-011).</li>
 *   <li>Telefone válido com cadastro → comando de link web (T-037, RF-10) se
 *       aplicável; senão delega para {@link WhatsappExpenseProcessor}
 *       (parser do Gemini ou fallback heurístico → expense + confirmação).</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InboundMessageService {

    private final WhatsappMessageRepository repository;
    private final PhoneNumberService phoneNumberService;
    private final UserService userService;
    private final ConversationService conversationService;
    private final WebLinkCommandHandler webLinkCommandHandler;
    private final WhatsappExpenseProcessor expenseProcessor;

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

        // saveAndFlush força o INSERT antes de qualquer efeito colateral
        // (envio de WhatsApp). O Evolution Go entrega o mesmo webhook 2x
        // simultaneamente; sem flush imediato, ambas as threads passam o
        // pre-check, enviam confirmações duplicadas, e só a segunda falha
        // no commit. A UNIQUE em evolution_msg_id captura aqui — convertemos
        // em DuplicateWebhookException, que o controller mapeia pra 200 OK.
        WhatsappMessage saved;
        try {
            saved = repository.saveAndFlush(message);
        } catch (DataIntegrityViolationException e) {
            log.info("Webhook duplicado descartado msgId={}", msgId);
            throw new DuplicateWebhookException(msgId);
        }

        if (normalizedPhone == null) {
            return saved;
        }

        if (webLinkCommandHandler.tryHandle(normalizedPhone, content, saved)) {
            return saved;
        }

        User user = saved.getUserId() != null
                ? userService.findOrNull(normalizedPhone)
                : null;

        if (user == null) {
            conversationService.handleAwaitingName(normalizedPhone, content);
        } else {
            expenseProcessor.process(user, saved);
        }

        return saved;
    }
}

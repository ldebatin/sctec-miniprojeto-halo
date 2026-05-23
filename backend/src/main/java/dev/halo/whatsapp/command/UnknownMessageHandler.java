package dev.halo.whatsapp.command;

import dev.halo.whatsapp.EvolutionClient;
import dev.halo.whatsapp.TutorialMessage;
import dev.halo.whatsapp.WhatsappMessage;
import dev.halo.whatsapp.WhatsappMessageStatus;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Resposta de ajuda quando o pipeline não reconheceu a mensagem como gasto,
 * comando de resumo nem link da web (RF-03/RF-04 — feedback ao usuário).
 *
 * Disparado pelo {@code InboundMessageService} quando o
 * {@code WhatsappExpenseProcessor} retorna {@link WhatsappMessageStatus#NOT_UNDERSTOOD}.
 *
 * Substitui o silêncio anterior: "Gazolina" (sem valor) era marcado como
 * IGNORED sem feedback, fazendo o usuário pensar que o bot estava quebrado.
 * Agora recebe uma mensagem com exemplos concretos de como registrar gasto,
 * pedir resumo e acessar a web.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UnknownMessageHandler {

    static final String HELP_MESSAGE = "Não entendi sua mensagem 🤔\n\n" + TutorialMessage.TEXT;

    private final EvolutionClient evolutionClient;

    /**
     * Envia a mensagem de ajuda e marca a {@link WhatsappMessage} como
     * {@link WhatsappMessageStatus#NOT_UNDERSTOOD}. Caller deve passar o
     * telefone já normalizado em E.164.
     */
    public void sendHelp(String phoneE164, WhatsappMessage message) {
        evolutionClient.sendText(phoneE164, HELP_MESSAGE);
        message.setStatus(WhatsappMessageStatus.NOT_UNDERSTOOD);
        message.setProcessedAt(Instant.now());
        log.info("Mensagem de ajuda enviada phone={} msgId={}",
                phoneE164, message.getEvolutionMsgId());
    }
}

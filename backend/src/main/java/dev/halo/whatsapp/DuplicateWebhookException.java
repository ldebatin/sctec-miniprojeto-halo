package dev.halo.whatsapp;

/**
 * Sinaliza que o backend recebeu um webhook com {@code evolution_msg_id} já
 * inserido por outra thread/instância. O Evolution Go pode entregar o mesmo
 * evento mais de uma vez simultaneamente, e a UNIQUE em
 * {@code whatsapp_messages.evolution_msg_id} captura a corrida entre o
 * pre-check de idempotência e o INSERT.
 *
 * O {@code EvolutionWebhookController} traduz isto em 200 OK sem reprocessar.
 */
public class DuplicateWebhookException extends RuntimeException {

    private final String msgId;

    public DuplicateWebhookException(String msgId) {
        super("Webhook duplicado para msgId=" + msgId);
        this.msgId = msgId;
    }

    public String getMsgId() {
        return msgId;
    }
}

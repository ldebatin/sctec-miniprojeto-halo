package dev.halo.whatsapp;

/**
 * Estado de processamento da mensagem no log {@code whatsapp_messages}.
 *
 * T-009 só usa {@code RECEIVED} (gravação inicial pelo webhook). Os demais
 * estados serão preenchidos por tasks futuras: {@code PROCESSED} (T-013, parser
 * de gasto), {@code IGNORED} (mensagens não-gasto), {@code FAILED} (erros).
 */
public enum WhatsappMessageStatus {
    RECEIVED,
    PROCESSED,
    IGNORED,
    FAILED
}

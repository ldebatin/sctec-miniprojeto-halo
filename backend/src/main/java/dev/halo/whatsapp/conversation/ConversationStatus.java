package dev.halo.whatsapp.conversation;

/**
 * Estado conversacional atual de um telefone (analise-tecnica.md §6.2 / §7.1).
 *
 * T-011 só conhece {@code AWAITING_NAME} — outros estados (ex.: edição de
 * categoria de gasto) entram em tasks futuras.
 */
public enum ConversationStatus {
    AWAITING_NAME
}

package dev.halo.whatsapp;

/**
 * Direção da mensagem no log {@code whatsapp_messages} (analise-tecnica.md §6.2).
 *
 * Valores espelham o CHECK do schema (V1__init.sql): apenas {@code IN} e {@code OUT}.
 */
public enum WhatsappDirection {
    IN,
    OUT
}

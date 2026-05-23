package dev.halo.whatsapp;

/**
 * Estado de processamento da mensagem no log {@code whatsapp_messages}.
 *
 * <ul>
 *   <li>{@code RECEIVED} — gravação inicial pelo webhook (T-009).</li>
 *   <li>{@code PROCESSED} — gasto persistido + confirmação ou comando
 *       (resumo, link web) atendido.</li>
 *   <li>{@code IGNORED} — payload vazio/sem conteúdo útil; sem resposta
 *       (não vale a pena dar feedback ao usuário).</li>
 *   <li>{@code NOT_UNDERSTOOD} — parser não reconheceu como gasto nem como
 *       comando válido; respondemos com a mensagem de ajuda explicando
 *       como registrar gasto / pedir resumo / acessar a web.</li>
 *   <li>{@code FAILED} — erro no processamento (ex.: valor &lt;= 0 recusado
 *       pelo {@code ExpenseService}).</li>
 * </ul>
 */
public enum WhatsappMessageStatus {
    RECEIVED,
    PROCESSED,
    IGNORED,
    NOT_UNDERSTOOD,
    FAILED
}

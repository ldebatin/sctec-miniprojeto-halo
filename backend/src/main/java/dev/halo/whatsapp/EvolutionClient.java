package dev.halo.whatsapp;

/**
 * Cliente para envio de mensagens via Evolution Go (analise-tecnica.md §8.3).
 *
 * Evolução:
 * <ul>
 *   <li>T-011 — interface + {@link #sendText} sobre {@code RestClient}.</li>
 *   <li>T-012 — retry + circuit breaker via Resilience4j.</li>
 *   <li>T-042 — {@link #sendMedia} para o gráfico do resumo mensal (RF-15).</li>
 * </ul>
 */
public interface EvolutionClient {

    /**
     * Envia uma mensagem de texto para o telefone informado.
     *
     * @param phoneE164 telefone destinatário em E.164 ({@code +5547999999999})
     * @param text      conteúdo da mensagem
     */
    void sendText(String phoneE164, String text);

    /**
     * Envia uma imagem para o telefone informado (T-042, RF-15). A imagem
     * vai codificada em base64 no body; o {@code caption} aparece abaixo
     * dela no WhatsApp.
     *
     * @param phoneE164  telefone destinatário em E.164
     * @param imageBytes bytes do PNG/JPEG (até alguns MB no WhatsApp)
     * @param mimeType   ex.: {@code image/png}
     * @param caption    texto opcional (pode ser {@code null} ou vazio)
     */
    void sendMedia(String phoneE164, byte[] imageBytes, String mimeType, String caption);
}

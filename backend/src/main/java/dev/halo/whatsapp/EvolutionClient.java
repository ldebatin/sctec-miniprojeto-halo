package dev.halo.whatsapp;

/**
 * Cliente para envio de mensagens via Evolution Go (analise-tecnica.md §8.3).
 *
 * Esta task (T-011) introduz a interface + uma implementação mínima sobre
 * Spring RestClient para que o fluxo de cadastro consiga responder via
 * WhatsApp. Retry exponencial e circuit breaker (Resilience4j) entram em T-012.
 */
public interface EvolutionClient {

    /**
     * Envia uma mensagem de texto para o telefone informado.
     *
     * @param phoneE164 telefone destinatário em E.164 ({@code +5547999999999})
     * @param text      conteúdo da mensagem
     */
    void sendText(String phoneE164, String text);
}

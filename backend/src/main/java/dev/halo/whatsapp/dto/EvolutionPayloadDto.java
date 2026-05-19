package dev.halo.whatsapp.dto;

/**
 * Payload do webhook do Evolution Go, conforme docs/analise-tecnica.md §8.2.
 *
 * Campos desconhecidos são tolerados (Spring Boot configura o ObjectMapper
 * com FAIL_ON_UNKNOWN_PROPERTIES=false por padrão), então futuras evoluções
 * do payload da Evolution não quebram a deserialização.
 */
public record EvolutionPayloadDto(
        String event,
        String instance,
        Data data
) {

    public record Data(
            Key key,
            Message message,
            Long messageTimestamp,
            String pushName
    ) {}

    public record Key(
            String id,
            String remoteJid,
            Boolean fromMe
    ) {}

    public record Message(
            String conversation
    ) {}
}

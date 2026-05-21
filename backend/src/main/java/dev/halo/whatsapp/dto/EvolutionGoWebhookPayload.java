package dev.halo.whatsapp.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Payload do webhook do Evolution Go (envelope estilo Baileys, PascalCase).
 *
 * <p>Difere do contrato originalmente especulado em analise-tecnica.md §8.2
 * (que era baseado no Evolution API Node v2): o Evolution Go envia
 * {@code event="Message"}, top-level {@code instanceName}, e nesta {@code data}
 * estruturada como {@code Info} + {@code Message} em PascalCase.
 *
 * <p>Usamos {@link #toCanonical()} para converter para o modelo interno
 * {@link EvolutionPayloadDto} consumido pelo {@link dev.halo.whatsapp.InboundMessageService}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EvolutionGoWebhookPayload(
        String event,
        String instanceName,
        Data data
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Data(
            @JsonProperty("Info") Info info,
            @JsonProperty("Message") Message message
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Info(
            @JsonProperty("ID") String id,
            @JsonProperty("Chat") String chat,
            @JsonProperty("IsFromMe") Boolean isFromMe,
            @JsonProperty("PushName") String pushName
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Message(String conversation) {}

    public EvolutionPayloadDto toCanonical() {
        Info info = data != null ? data.info() : null;
        Message msg = data != null ? data.message() : null;

        EvolutionPayloadDto.Key key = info != null
                ? new EvolutionPayloadDto.Key(info.id(), info.chat(), info.isFromMe())
                : null;
        EvolutionPayloadDto.Message canonicalMsg = msg != null
                ? new EvolutionPayloadDto.Message(msg.conversation())
                : null;
        String pushName = info != null ? info.pushName() : null;

        EvolutionPayloadDto.Data canonicalData = new EvolutionPayloadDto.Data(
                key, canonicalMsg, null, pushName);
        return new EvolutionPayloadDto(event, instanceName, canonicalData);
    }
}

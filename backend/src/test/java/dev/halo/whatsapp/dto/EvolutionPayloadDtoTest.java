package dev.halo.whatsapp.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/**
 * Verifica que o Jackson deserializa os 5 campos do payload §8.2:
 * data.key.id, data.key.remoteJid, data.message.conversation, data.pushName,
 * data.messageTimestamp (mais data.key.fromMe, usado pelo curto-circuito).
 */
class EvolutionPayloadDtoTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void deserializa_payload_completo_da_analise_tecnica() throws Exception {
        String json = """
                {
                  "event": "messages.upsert",
                  "instance": "halo-bot",
                  "data": {
                    "key": {
                      "id": "ABCD123",
                      "remoteJid": "5547999999999@s.whatsapp.net",
                      "fromMe": false
                    },
                    "message": { "conversation": "Mercado 87,30" },
                    "messageTimestamp": 1716042000,
                    "pushName": "Maria"
                  }
                }""";

        EvolutionPayloadDto dto = objectMapper.readValue(json, EvolutionPayloadDto.class);

        assertThat(dto.event()).isEqualTo("messages.upsert");
        assertThat(dto.instance()).isEqualTo("halo-bot");
        assertThat(dto.data().key().id()).isEqualTo("ABCD123");
        assertThat(dto.data().key().remoteJid()).isEqualTo("5547999999999@s.whatsapp.net");
        assertThat(dto.data().key().fromMe()).isFalse();
        assertThat(dto.data().message().conversation()).isEqualTo("Mercado 87,30");
        assertThat(dto.data().pushName()).isEqualTo("Maria");
        assertThat(dto.data().messageTimestamp()).isEqualTo(1716042000L);
    }

    @Test
    void tolera_campos_desconhecidos() throws Exception {
        String json = """
                {
                  "event": "messages.upsert",
                  "instance": "halo-bot",
                  "extraField": "should be ignored",
                  "data": {
                    "key": { "id": "X", "fromMe": false, "extra": 1 },
                    "messageTimestamp": 1716042000
                  }
                }""";

        ObjectMapper lenient = objectMapper.copy()
                .configure(com.fasterxml.jackson.databind.DeserializationFeature
                        .FAIL_ON_UNKNOWN_PROPERTIES, false);

        EvolutionPayloadDto dto = lenient.readValue(json, EvolutionPayloadDto.class);
        assertThat(dto.data().key().id()).isEqualTo("X");
    }
}

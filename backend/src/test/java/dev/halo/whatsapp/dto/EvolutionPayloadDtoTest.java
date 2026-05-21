package dev.halo.whatsapp.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/**
 * Verifica que o Jackson deserializa o payload real do Evolution Go
 * (envelope Baileys / PascalCase) e que {@link EvolutionGoWebhookPayload#toCanonical()}
 * preenche os 5 campos consumidos pelo {@code InboundMessageService}:
 * id, chat (remoteJid), fromMe, conversation, pushName.
 */
class EvolutionPayloadDtoTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void deserializa_payload_real_do_evolution_go_e_converte_para_canonico() throws Exception {
        String json = """
                {
                  "event": "Message",
                  "instanceName": "halo-bot",
                  "instanceId": "9b02de74-26d8-4d55-8388-8fff1090b39f",
                  "instanceToken": "tok-redacted",
                  "data": {
                    "Info": {
                      "ID": "ABCD123",
                      "Chat": "5547999999999@s.whatsapp.net",
                      "Sender": "5547999999999@s.whatsapp.net",
                      "IsFromMe": false,
                      "IsGroup": false,
                      "PushName": "Maria",
                      "Timestamp": "2026-05-20T21:28:23-03:00",
                      "Type": "text"
                    },
                    "Message": {
                      "conversation": "Mercado 87,30",
                      "messageContextInfo": { "deviceListMetadataVersion": 2 }
                    }
                  }
                }""";

        EvolutionGoWebhookPayload wire = objectMapper.readValue(json, EvolutionGoWebhookPayload.class);
        assertThat(wire.event()).isEqualTo("Message");
        assertThat(wire.instanceName()).isEqualTo("halo-bot");
        assertThat(wire.data().info().id()).isEqualTo("ABCD123");
        assertThat(wire.data().info().chat()).isEqualTo("5547999999999@s.whatsapp.net");
        assertThat(wire.data().info().isFromMe()).isFalse();
        assertThat(wire.data().info().pushName()).isEqualTo("Maria");
        assertThat(wire.data().message().conversation()).isEqualTo("Mercado 87,30");

        EvolutionPayloadDto canonical = wire.toCanonical();
        assertThat(canonical.event()).isEqualTo("Message");
        assertThat(canonical.instance()).isEqualTo("halo-bot");
        assertThat(canonical.data().key().id()).isEqualTo("ABCD123");
        assertThat(canonical.data().key().remoteJid()).isEqualTo("5547999999999@s.whatsapp.net");
        assertThat(canonical.data().key().fromMe()).isFalse();
        assertThat(canonical.data().message().conversation()).isEqualTo("Mercado 87,30");
        assertThat(canonical.data().pushName()).isEqualTo("Maria");
    }

    @Test
    void tolera_campos_desconhecidos_no_envelope_e_em_info() throws Exception {
        String json = """
                {
                  "event": "Message",
                  "instanceName": "halo-bot",
                  "campoExtraDesconhecido": "ignored",
                  "data": {
                    "Info": {
                      "ID": "X",
                      "IsFromMe": false,
                      "CampoExtra": 1
                    },
                    "Message": { "conversation": "olá" },
                    "OutroCampo": true
                  }
                }""";

        EvolutionGoWebhookPayload wire = objectMapper.readValue(json, EvolutionGoWebhookPayload.class);
        assertThat(wire.data().info().id()).isEqualTo("X");
        assertThat(wire.toCanonical().data().key().id()).isEqualTo("X");
    }
}

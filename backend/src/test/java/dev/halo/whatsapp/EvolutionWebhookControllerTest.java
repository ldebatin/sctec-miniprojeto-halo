package dev.halo.whatsapp;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.halo.whatsapp.config.EvolutionProperties;
import dev.halo.whatsapp.dto.EvolutionPayloadDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class EvolutionWebhookControllerTest {

    private static final String API_KEY = "test-secret";
    private static final String PAYLOAD = """
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

    private static final String FROM_ME_PAYLOAD = """
            {
              "event": "messages.upsert",
              "instance": "halo-bot",
              "data": {
                "key": {
                  "id": "OUT-1",
                  "remoteJid": "5547999999999@s.whatsapp.net",
                  "fromMe": true
                },
                "message": { "conversation": "resposta do bot" },
                "messageTimestamp": 1716042100,
                "pushName": "Maria"
              }
            }""";

    private MockMvc mvc;
    private InboundMessageService inboundMessageService;

    @BeforeEach
    void setUp() {
        inboundMessageService = mock(InboundMessageService.class);
        EvolutionWebhookController controller = new EvolutionWebhookController(
                new EvolutionProperties(API_KEY, null, null, null), inboundMessageService);
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void sem_apikey_retorna_401() throws Exception {
        mvc.perform(post("/webhooks/evolution")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PAYLOAD))
                .andExpect(status().isUnauthorized());
        verify(inboundMessageService, never()).record(any());
    }

    @Test
    void com_apikey_errado_retorna_401() throws Exception {
        mvc.perform(post("/webhooks/evolution")
                        .header("apikey", "wrong-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PAYLOAD))
                .andExpect(status().isUnauthorized());
        verify(inboundMessageService, never()).record(any());
    }

    @Test
    void com_apikey_correto_retorna_200_e_persiste() throws Exception {
        mvc.perform(post("/webhooks/evolution")
                        .header("apikey", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PAYLOAD))
                .andExpect(status().isOk());
        verify(inboundMessageService, times(1)).record(any(EvolutionPayloadDto.class));
    }

    @Test
    void fromMe_true_retorna_200_sem_persistir() throws Exception {
        mvc.perform(post("/webhooks/evolution")
                        .header("apikey", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(FROM_ME_PAYLOAD))
                .andExpect(status().isOk());
        verify(inboundMessageService, never()).record(any());
    }
}

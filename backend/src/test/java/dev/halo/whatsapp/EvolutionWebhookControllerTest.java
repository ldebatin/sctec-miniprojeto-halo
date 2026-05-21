package dev.halo.whatsapp;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.halo.whatsapp.dto.EvolutionPayloadDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class EvolutionWebhookControllerTest {

    private static final String PAYLOAD = """
            {
              "event": "Message",
              "instanceName": "halo-bot",
              "data": {
                "Info": {
                  "ID": "ABCD123",
                  "Chat": "5547999999999@s.whatsapp.net",
                  "IsFromMe": false,
                  "PushName": "Maria"
                },
                "Message": { "conversation": "Mercado 87,30" }
              }
            }""";

    private static final String FROM_ME_PAYLOAD = """
            {
              "event": "Message",
              "instanceName": "halo-bot",
              "data": {
                "Info": {
                  "ID": "OUT-1",
                  "Chat": "5547999999999@s.whatsapp.net",
                  "IsFromMe": true,
                  "PushName": "Maria"
                },
                "Message": { "conversation": "resposta do bot" }
              }
            }""";

    private MockMvc mvc;
    private InboundMessageService inboundMessageService;

    @BeforeEach
    void setUp() {
        inboundMessageService = mock(InboundMessageService.class);
        EvolutionWebhookController controller = new EvolutionWebhookController(inboundMessageService);
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void payload_valido_retorna_200_e_persiste() throws Exception {
        mvc.perform(post("/webhooks/evolution")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PAYLOAD))
                .andExpect(status().isOk());
        verify(inboundMessageService, times(1)).record(any(EvolutionPayloadDto.class));
    }

    @Test
    void webhook_duplicado_retorna_200_sem_propagar_excecao() throws Exception {
        doThrow(new DuplicateWebhookException("ABCD123"))
                .when(inboundMessageService).record(any(EvolutionPayloadDto.class));

        mvc.perform(post("/webhooks/evolution")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PAYLOAD))
                .andExpect(status().isOk());
    }

    @Test
    void fromMe_true_retorna_200_sem_persistir() throws Exception {
        mvc.perform(post("/webhooks/evolution")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(FROM_ME_PAYLOAD))
                .andExpect(status().isOk());
        verify(inboundMessageService, never()).record(any());
    }
}

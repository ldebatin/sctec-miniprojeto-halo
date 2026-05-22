package dev.halo.whatsapp.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.halo.ai.GeminiClient;
import dev.halo.user.User;
import dev.halo.user.UserRepository;
import dev.halo.whatsapp.EvolutionClient;
import dev.halo.whatsapp.InboundMessageService;
import dev.halo.whatsapp.WhatsappMessageRepository;
import dev.halo.whatsapp.WhatsappMessageStatus;
import dev.halo.whatsapp.dto.EvolutionPayloadDto;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integração T-037: comando de link antes do parser de gasto (RF-10).
 */
@SpringBootTest
@Testcontainers
@Transactional
@TestPropertySource(properties = {
        "halo.web.public-url=https://app.halo.test",
        "halo.web.link-triggers=site,portal"
})
class WebLinkCommandIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    private static final String PHONE = "+5547999888777";
    private static final String JID = "5547999888777@s.whatsapp.net";

    @Autowired
    private InboundMessageService inboundMessageService;

    @Autowired
    private WhatsappMessageRepository messageRepository;

    @Autowired
    private UserRepository userRepository;

    @MockBean
    private EvolutionClient evolutionClient;

    @MockBean
    private GeminiClient geminiClient;

    private static EvolutionPayloadDto payload(String msgId, String text) {
        return new EvolutionPayloadDto(
                "messages.upsert",
                "halo-bot",
                new EvolutionPayloadDto.Data(
                        new EvolutionPayloadDto.Key(msgId, JID, false),
                        new EvolutionPayloadDto.Message(text),
                        1_716_042_000L,
                        "Maria"
                )
        );
    }

    private User existingUser() {
        User user = new User();
        user.setName("Maria");
        user.setPhone(PHONE);
        Instant now = Instant.now();
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        return userRepository.save(user);
    }

    @Test
    void comando_site_responde_com_url_e_nao_chama_gemini() {
        existingUser();

        inboundMessageService.record(payload("WEB-1", "site"));

        var saved = messageRepository.findAll().stream()
                .filter(m -> "WEB-1".equals(m.getEvolutionMsgId()))
                .findFirst()
                .orElseThrow();

        assertThat(saved.getStatus()).isEqualTo(WhatsappMessageStatus.PROCESSED);
        verify(evolutionClient).sendText(eq(PHONE), contains("https://app.halo.test"));
        verify(geminiClient, never()).parseExpense(any(), anyList(), any());
    }

    @Test
    void gatilho_customizado_portal_via_property() {
        existingUser();

        inboundMessageService.record(payload("WEB-2", "portal"));

        verify(evolutionClient).sendText(eq(PHONE), contains("https://app.halo.test"));
        verify(geminiClient, never()).parseExpense(any(), anyList(), any());
    }

    @Test
    void gatilho_fora_da_lista_customizada_nao_dispara_comando() {
        existingUser();
        when(geminiClient.parseExpense(any(), anyList(), any())).thenReturn(null);

        inboundMessageService.record(payload("WEB-3", "web"));

        verify(evolutionClient, never()).sendText(eq(PHONE), contains("https://app.halo.test"));
    }
}

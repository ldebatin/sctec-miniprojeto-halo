package dev.halo.whatsapp.conversation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import dev.halo.user.User;
import dev.halo.user.UserRepository;
import dev.halo.whatsapp.EvolutionClient;
import dev.halo.whatsapp.InboundMessageService;
import dev.halo.whatsapp.dto.EvolutionPayloadDto;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Cobre os 4 critérios de aceitação da T-011 — fluxo conversacional
 * AWAITING_NAME (RF-01, analise-tecnica.md §7.1).
 *
 * Exercita o caminho real do webhook: {@link InboundMessageService#record}
 * dispara o {@link ConversationService}. O {@link EvolutionClient} é
 * substituído por um mock para inspecionar os envios.
 */
@SpringBootTest
@Testcontainers
@Transactional
class ConversationFlowIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    private static final String PHONE_E164 = "+5547999999999";
    private static final String JID = "5547999999999@s.whatsapp.net";
    private static final String ASK_NAME = "Olá! Eu sou o Halo. Qual seu nome?";

    @Autowired
    private InboundMessageService inboundMessageService;

    @Autowired
    private ConversationStateRepository conversationStateRepository;

    @Autowired
    private UserRepository userRepository;

    @MockBean
    private EvolutionClient evolutionClient;

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

    @Test
    void primeira_mensagem_cria_AWAITING_NAME_e_pergunta_nome() {
        inboundMessageService.record(payload("MSG-1", "Oi"));

        Optional<ConversationState> state = conversationStateRepository.findByPhone(PHONE_E164);
        assertThat(state).isPresent();
        assertThat(state.get().getState()).isEqualTo(ConversationStatus.AWAITING_NAME);
        assertThat(state.get().getExpiresAt()).isAfter(Instant.now());
        verify(evolutionClient, times(1)).sendText(eq(PHONE_E164), eq(ASK_NAME));
        assertThat(userRepository.findByPhone(PHONE_E164)).isEmpty();
    }

    @Test
    void segunda_mensagem_cria_user_apaga_estado_e_envia_boas_vindas() {
        // 1ª mensagem deixa o estado em AWAITING_NAME
        inboundMessageService.record(payload("MSG-1", "Oi"));

        // 2ª mensagem com o nome
        inboundMessageService.record(payload("MSG-2", "Maria"));

        assertThat(conversationStateRepository.findByPhone(PHONE_E164)).isEmpty();
        Optional<User> user = userRepository.findByPhone(PHONE_E164);
        assertThat(user).isPresent();
        assertThat(user.get().getName()).isEqualTo("Maria");
        verify(evolutionClient).sendText(PHONE_E164, "Bem-vindo(a), Maria!");
    }

    @Test
    void estado_expirado_e_descartado_e_proxima_mensagem_reinicia_fluxo() {
        // Pré-popula um estado expirado (expires_at no passado)
        ConversationState expired = new ConversationState();
        expired.setPhone(PHONE_E164);
        expired.setState(ConversationStatus.AWAITING_NAME);
        Instant past = Instant.now().minusSeconds(60);
        expired.setUpdatedAt(past);
        expired.setExpiresAt(past);
        conversationStateRepository.saveAndFlush(expired);

        inboundMessageService.record(payload("MSG-1", "Maria"));

        // A mensagem deve ser tratada como "primeira mensagem" — novo estado
        // criado e pergunta reenviada (e NÃO criar User com "Maria").
        Optional<ConversationState> reloaded = conversationStateRepository.findByPhone(PHONE_E164);
        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().getId()).isNotEqualTo(expired.getId());
        assertThat(reloaded.get().getExpiresAt()).isAfter(Instant.now());
        verify(evolutionClient).sendText(PHONE_E164, ASK_NAME);
        assertThat(userRepository.findByPhone(PHONE_E164)).isEmpty();
    }

    @Test
    void nome_curto_reenvia_a_pergunta_sem_criar_user() {
        inboundMessageService.record(payload("MSG-1", "Oi"));
        inboundMessageService.record(payload("MSG-2", "M"));

        // Estado permanece AWAITING_NAME
        assertThat(conversationStateRepository.findByPhone(PHONE_E164)).isPresent();
        // Nenhum usuário criado
        assertThat(userRepository.findByPhone(PHONE_E164)).isEmpty();
        // sendText("Qual seu nome?") deve ter sido chamado 2x (1x cada msg)
        verify(evolutionClient, times(2)).sendText(PHONE_E164, ASK_NAME);
    }
}

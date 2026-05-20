package dev.halo.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;

import dev.halo.user.User;
import dev.halo.user.UserRepository;
import dev.halo.whatsapp.dto.EvolutionPayloadDto;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integração T-010 com Testcontainers + Postgres 16: cobre persistência idempotente
 * (T-009) e resolução de usuário por telefone (T-010) — populando {@code user_id}
 * quando há cadastro e tolerando telefone inválido.
 */
@SpringBootTest
@Testcontainers
@Transactional
class InboundMessageServiceIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private InboundMessageService service;

    @Autowired
    private WhatsappMessageRepository repository;

    @Autowired
    private UserRepository userRepository;

    private static EvolutionPayloadDto payload(String msgId, String jid, String text) {
        return new EvolutionPayloadDto(
                "messages.upsert",
                "halo-bot",
                new EvolutionPayloadDto.Data(
                        new EvolutionPayloadDto.Key(msgId, jid, false),
                        new EvolutionPayloadDto.Message(text),
                        1_716_042_000L,
                        "Maria"
                )
        );
    }

    private User existingUser(String phoneE164, String name) {
        User user = new User();
        user.setName(name);
        user.setPhone(phoneE164);
        Instant now = Instant.now();
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        return userRepository.save(user);
    }

    @Test
    void mesma_msgId_recebida_2x_cria_apenas_1_linha() {
        EvolutionPayloadDto p = payload("DUP-1", "5547999999999@s.whatsapp.net", "Mercado 87,30");

        WhatsappMessage first = service.record(p);
        WhatsappMessage second = service.record(p);

        assertThat(repository.count()).isEqualTo(1L);
        assertThat(second.getId()).isEqualTo(first.getId());
    }

    @Test
    void persiste_com_direction_IN_status_RECEIVED_e_userId_nulo_quando_nao_ha_usuario() {
        EvolutionPayloadDto p = payload("IN-1", "5547888888888@s.whatsapp.net", "Uber 25");

        WhatsappMessage saved = service.record(p);

        WhatsappMessage reloaded = repository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getDirection()).isEqualTo(WhatsappDirection.IN);
        assertThat(reloaded.getStatus()).isEqualTo(WhatsappMessageStatus.RECEIVED);
        assertThat(reloaded.getEvolutionMsgId()).isEqualTo("IN-1");
        assertThat(reloaded.getContent()).isEqualTo("Uber 25");
        assertThat(reloaded.getReceivedAt()).isNotNull();
        assertThat(reloaded.getProcessedAt()).isNull();
        assertThat(reloaded.getUserId()).isNull();
    }

    @Test
    void popula_userId_quando_telefone_bate_com_usuario_cadastrado() {
        User maria = existingUser("+5547777777777", "Maria");
        EvolutionPayloadDto p = payload("MATCH-1", "5547777777777@s.whatsapp.net", "Mercado 50");

        WhatsappMessage saved = service.record(p);

        assertThat(saved.getUserId()).isEqualTo(maria.getId());
    }

    @Test
    void telefone_invalido_persiste_a_mensagem_com_userId_nulo() {
        // remoteJid muito curto — InvalidPhoneException é tratada dentro do service.
        EvolutionPayloadDto p = payload("BAD-1", "abc@s.whatsapp.net", "??");

        WhatsappMessage saved = service.record(p);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getUserId()).isNull();
        assertThat(saved.getStatus()).isEqualTo(WhatsappMessageStatus.RECEIVED);
    }
}

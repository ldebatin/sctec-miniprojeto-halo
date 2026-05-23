package dev.halo.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.halo.ai.ExpenseParseResult;
import dev.halo.ai.GeminiClient;
import dev.halo.expense.Expense;
import dev.halo.expense.ExpenseRepository;
import dev.halo.user.User;
import dev.halo.user.UserRepository;
import dev.halo.whatsapp.dto.EvolutionPayloadDto;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
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
 * Smoke E2E da Release 1 — webhook → parser → expense → confirmação (T-018).
 *
 * Usa {@link GeminiClient} e {@link EvolutionClient} mockados para isolar a
 * orquestração do backend dos clientes externos. A persistência é real via
 * Testcontainers + Postgres.
 */
@SpringBootTest
@Testcontainers
@Transactional
class WebhookE2EIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    private static final String PHONE = "+5547999999999";
    private static final String JID = "5547999999999@s.whatsapp.net";

    @Autowired
    private InboundMessageService inboundMessageService;

    @Autowired
    private WhatsappMessageRepository messageRepository;

    @Autowired
    private ExpenseRepository expenseRepository;

    @Autowired
    private UserRepository userRepository;

    @MockBean
    private GeminiClient geminiClient;

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

    private User insertExistingUser() {
        User user = new User();
        user.setPhone(PHONE);
        user.setName("Maria");
        Instant now = Instant.now();
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        return userRepository.save(user);
    }

    @Test
    void usuario_cadastrado_envia_gasto_e_recebe_confirmacao_formatada() {
        insertExistingUser();
        when(geminiClient.parseExpense(anyString(), anyList(), any())).thenReturn(
                new ExpenseParseResult(
                        "Mercado", new BigDecimal("87.30"), "Mercado",
                        LocalDate.of(2026, 5, 18)));

        inboundMessageService.record(payload("E2E-1", "Mercado 87,30"));

        assertThat(expenseRepository.count()).isEqualTo(1L);
        Expense expense = expenseRepository.findAll().get(0);
        assertThat(expense.getDescription()).isEqualTo("Mercado");
        assertThat(expense.getAmount()).isEqualByComparingTo(new BigDecimal("87.30"));

        WhatsappMessage saved = messageRepository.findByEvolutionMsgId("E2E-1").orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(WhatsappMessageStatus.PROCESSED);
        assertThat(saved.getProcessedAt()).isNotNull();

        verify(evolutionClient).sendText(
                PHONE,
                "Registrado: Mercado R$ 87,30 → Mercado. Data: 18/05. Use a web para alterar.");
    }

    @Test
    void usuario_cadastrado_com_mensagem_sem_gasto_marca_NOT_UNDERSTOOD_e_envia_ajuda() {
        insertExistingUser();
        when(geminiClient.parseExpense(anyString(), anyList(), any())).thenReturn(null);

        inboundMessageService.record(payload("E2E-IGN", "Oi, tudo bem?"));

        assertThat(expenseRepository.count()).isZero();
        WhatsappMessage saved = messageRepository.findByEvolutionMsgId("E2E-IGN").orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(WhatsappMessageStatus.NOT_UNDERSTOOD);
        // Substitui o silêncio anterior: agora respondemos com a mensagem de ajuda.
        verify(evolutionClient).sendText(anyString(), contains("Não entendi"));
    }

    @Test
    void usuario_cadastrado_recebe_fallback_quando_Gemini_falha_mas_regex_acha_valor() {
        insertExistingUser();
        // Gemini retorna null (mesmo cenário do fallback heurístico — T-016)
        when(geminiClient.parseExpense(anyString(), anyList(), any())).thenReturn(null);

        inboundMessageService.record(payload("E2E-FB", "Padaria 12,50"));

        // Gasto persistido com "Sem categoria" via fallback
        Expense expense = expenseRepository.findAll().get(0);
        assertThat(expense.getAmount()).isEqualByComparingTo(new BigDecimal("12.50"));

        WhatsappMessage saved = messageRepository.findByEvolutionMsgId("E2E-FB").orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(WhatsappMessageStatus.PROCESSED);
        verify(evolutionClient).sendText(
                org.mockito.ArgumentMatchers.eq(PHONE),
                anyString());
    }

    @Test
    void usuario_novo_nao_aciona_parser_e_segue_para_AWAITING_NAME() {
        // Sem User cadastrado para esse phone → ConversationService entra (T-011).
        inboundMessageService.record(payload("E2E-NEW", "Oi"));

        assertThat(expenseRepository.count()).isZero();
        // Confirmação não enviada pelo expense processor; o ConversationService dispara
        // o "Qual seu nome?" no EvolutionClient.
        verify(evolutionClient).sendText(
                PHONE,
                "Olá! Eu sou o Halo. Qual seu nome?");
    }
}

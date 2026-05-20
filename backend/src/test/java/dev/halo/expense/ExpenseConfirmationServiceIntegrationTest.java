package dev.halo.expense;

import static org.mockito.Mockito.verify;

import dev.halo.ai.ExpenseParseResult;
import dev.halo.category.CategoryGlobal;
import dev.halo.category.CategoryGlobalRepository;
import dev.halo.user.User;
import dev.halo.user.UserRepository;
import dev.halo.whatsapp.EvolutionClient;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
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
 * Verifica que o {@link ExpenseConfirmationService} dispara
 * {@link EvolutionClient#sendText} com a mensagem formatada esperada
 * (critério final da T-015).
 */
@SpringBootTest
@Testcontainers
@Transactional
class ExpenseConfirmationServiceIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private ExpenseService expenseService;

    @Autowired
    private ExpenseConfirmationService confirmationService;

    @Autowired
    private CategoryGlobalRepository categoryGlobalRepository;

    @Autowired
    private UserRepository userRepository;

    @MockBean
    private EvolutionClient evolutionClient;

    private User insertUser() {
        User user = new User();
        user.setPhone("+5547999999999");
        user.setName("Maria");
        Instant now = Instant.now();
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        return userRepository.save(user);
    }

    private CategoryGlobal insertGlobal(String name) {
        CategoryGlobal g = new CategoryGlobal();
        g.setId(UUID.randomUUID());
        g.setName(name);
        g.setIcon("circle");
        g.setColor("#000000");
        g.setKeywords(new String[0]);
        return categoryGlobalRepository.save(g);
    }

    @Test
    void envia_mensagem_formatada_para_o_telefone_do_usuario() {
        User user = insertUser();
        insertGlobal("Mercado");
        Expense expense = expenseService.createFromWhatsapp(
                user,
                new ExpenseParseResult(
                        "Mercado",
                        new BigDecimal("87.30"),
                        "Mercado",
                        LocalDate.of(2026, 5, 18)),
                "Mercado 87,30");

        confirmationService.confirm(user, expense);

        verify(evolutionClient).sendText(
                "+5547999999999",
                "Registrado: Mercado R$ 87,30 → Mercado. Data: 18/05. Use a web para alterar.");
    }

    @Test
    void usa_Sem_categoria_quando_resolver_caiu_no_fallback() {
        User user = insertUser();
        // sem inserir global "Petshop", o resolver cai em "Sem categoria" (seed V3)
        Expense expense = expenseService.createFromWhatsapp(
                user,
                new ExpenseParseResult(
                        "Petshop",
                        new BigDecimal("45.00"),
                        "Petshop",
                        LocalDate.of(2026, 1, 7)),
                "Petshop 45");

        confirmationService.confirm(user, expense);

        verify(evolutionClient).sendText(
                "+5547999999999",
                "Registrado: Petshop R$ 45,00 → Sem categoria. Data: 07/01. Use a web para alterar.");
    }
}

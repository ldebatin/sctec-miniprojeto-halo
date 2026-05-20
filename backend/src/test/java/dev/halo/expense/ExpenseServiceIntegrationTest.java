package dev.halo.expense;

import static org.assertj.core.api.Assertions.assertThat;

import dev.halo.ai.ExpenseParseResult;
import dev.halo.category.Category;
import dev.halo.category.CategoryGlobal;
import dev.halo.category.CategoryGlobalRepository;
import dev.halo.category.CategoryRepository;
import dev.halo.user.User;
import dev.halo.user.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Cobre os 4 critérios de aceitação da T-014:
 * match case-insensitive priorizando customs do usuário; fallback "Sem categoria"
 * quando hint não bate; source=WHATSAPP + raw_message + occurredAt (parse ou hoje);
 * amount ≤ 0 rejeitado com log.
 */
@SpringBootTest
@Testcontainers
@Transactional
class ExpenseServiceIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private ExpenseService expenseService;

    @Autowired
    private ExpenseRepository expenseRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private CategoryGlobalRepository categoryGlobalRepository;

    @Autowired
    private UserRepository userRepository;

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
        // Reutiliza se já existe (V4 seed das 12 globais do PRD pode ter inserido).
        return categoryGlobalRepository.findByNameIgnoreCase(name).orElseGet(() -> {
            CategoryGlobal global = new CategoryGlobal();
            global.setId(UUID.randomUUID());
            global.setName(name);
            global.setIcon("circle");
            global.setColor("#000000");
            global.setKeywords(new String[0]);
            return categoryGlobalRepository.save(global);
        });
    }

    private Category insertUserCategory(User user, String name) {
        Instant now = Instant.now();
        Category cat = new Category();
        cat.setUserId(user.getId());
        cat.setName(name);
        cat.setIcon("star");
        cat.setColor("#FF0000");
        cat.setActive(true);
        cat.setCreatedAt(now);
        cat.setUpdatedAt(now);
        return categoryRepository.save(cat);
    }

    private ExpenseParseResult parse(BigDecimal amount, String hint, LocalDate at) {
        return new ExpenseParseResult("Mercado", amount, hint, at);
    }

    @Test
    void categoria_customizada_tem_prioridade_sobre_global_com_mesmo_nome() {
        User user = insertUser();
        insertGlobal("Mercado");
        Category userMercado = insertUserCategory(user, "MERCADO"); // mesmo nome, capitalização diferente

        Expense saved = expenseService.createFromWhatsapp(
                user, parse(new BigDecimal("87.30"), "mercado", null), "Mercado 87,30");

        assertThat(saved).isNotNull();
        assertThat(saved.getCategoryId()).isEqualTo(userMercado.getId());
    }

    @Test
    void cai_em_global_quando_nao_ha_customizada() {
        User user = insertUser();
        CategoryGlobal mercado = insertGlobal("Mercado");

        Expense saved = expenseService.createFromWhatsapp(
                user, parse(new BigDecimal("50.00"), "Mercado", null), "Mercado 50");

        // O service faz lazy-copy: cria uma user-category com global_id = mercado.id
        Category resolved = categoryRepository.findById(saved.getCategoryId()).orElseThrow();
        assertThat(resolved.getGlobalId()).isEqualTo(mercado.getId());
        assertThat(resolved.getUserId()).isEqualTo(user.getId());
        assertThat(resolved.getName()).isEqualTo("Mercado");
    }

    @Test
    void hint_inexistente_cai_em_Sem_categoria() {
        User user = insertUser();
        // "Sem categoria" é seedada pela V3 — não precisa criar.
        Expense saved = expenseService.createFromWhatsapp(
                user, parse(new BigDecimal("12.50"), "Petshop", null), "Petshop 12,50");

        Category resolved = categoryRepository.findById(saved.getCategoryId()).orElseThrow();
        assertThat(resolved.getName()).isEqualToIgnoringCase("Sem categoria");
    }

    @Test
    void source_WHATSAPP_raw_message_e_occurred_at_sao_persistidos_corretamente() {
        User user = insertUser();
        insertGlobal("Mercado");

        Expense saved = expenseService.createFromWhatsapp(
                user, parse(new BigDecimal("9.99"), "Mercado", LocalDate.of(2026, 5, 1)),
                "Mercado 9,99 ontem");

        Expense reloaded = expenseRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getSource()).isEqualTo(ExpenseSource.WHATSAPP);
        assertThat(reloaded.getRawMessage()).isEqualTo("Mercado 9,99 ontem");
        assertThat(reloaded.getOccurredAt()).isEqualTo(LocalDate.of(2026, 5, 1));
    }

    @Test
    void occurred_at_nulo_default_para_hoje() {
        User user = insertUser();
        Expense saved = expenseService.createFromWhatsapp(
                user, parse(new BigDecimal("5.00"), null, null), "lanche");

        assertThat(saved.getOccurredAt()).isEqualTo(LocalDate.now());
    }

    @Test
    void valor_zero_e_rejeitado_com_log() {
        User user = insertUser();

        Expense saved = expenseService.createFromWhatsapp(
                user, parse(BigDecimal.ZERO, "Mercado", null), "Mercado 0");

        assertThat(saved).isNull();
        assertThat(expenseRepository.count()).isZero();
    }

    @Test
    void valor_negativo_e_rejeitado_com_log() {
        User user = insertUser();

        Expense saved = expenseService.createFromWhatsapp(
                user, parse(new BigDecimal("-10.00"), "Mercado", null), "Mercado -10");

        assertThat(saved).isNull();
        assertThat(expenseRepository.count()).isZero();
    }
}

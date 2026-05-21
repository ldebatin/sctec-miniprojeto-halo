package dev.halo.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.halo.auth.JwtService;
import dev.halo.category.Category;
import dev.halo.category.CategoryRepository;
import dev.halo.expense.Expense;
import dev.halo.expense.ExpenseRepository;
import dev.halo.expense.ExpenseSource;
import dev.halo.user.User;
import dev.halo.user.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integração do {@link ReportController} com JWT real e Postgres real.
 *
 * Cobre breakdown por categoria, ordenação por total desc, exclusão de
 * soft-deleted, isolamento entre usuários, ranges vazios e mês sem dados.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ReportControllerIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired private MockMvc mvc;
    @Autowired private UserRepository userRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ExpenseRepository expenseRepository;
    @Autowired private JwtService jwtService;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void monthly_sem_jwt_retorna_401() throws Exception {
        MvcResult res = mvc.perform(get("/reports/monthly")).andReturn();
        assertThat(res.getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void monthly_devolve_breakdown_com_percent_ordenado_desc() throws Exception {
        User user = criarUser("+5547620000001");
        Category mercado = criarCategoria(user, "Mercado", "#0000FF");
        Category lazer = criarCategoria(user, "Lazer", "#FF0000");
        criarExpense(user, mercado, "compras", "300", LocalDate.of(2026, 5, 10));
        criarExpense(user, mercado, "feira", "100", LocalDate.of(2026, 5, 15));
        criarExpense(user, lazer, "cinema", "100", LocalDate.of(2026, 5, 20));

        MvcResult res = mvc.perform(get("/reports/monthly")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt(user))
                        .param("month", "2026-05"))
                .andReturn();
        JsonNode body = objectMapper.readTree(res.getResponse().getContentAsString());

        assertThat(body.get("month").asText()).isEqualTo("2026-05");
        assertThat(body.get("from").asText()).isEqualTo("2026-05-01");
        assertThat(body.get("to").asText()).isEqualTo("2026-05-31");
        assertThat(body.get("total").decimalValue()).isEqualByComparingTo("500.00");

        JsonNode breakdown = body.get("breakdown");
        assertThat(breakdown).hasSize(2);
        // ordenado por total desc — Mercado 400 antes de Lazer 100
        assertThat(breakdown.get(0).get("name").asText()).isEqualTo("Mercado");
        assertThat(breakdown.get(0).get("total").decimalValue()).isEqualByComparingTo("400.00");
        assertThat(breakdown.get(0).get("percentage").decimalValue()).isEqualByComparingTo("80.00");
        assertThat(breakdown.get(0).get("color").asText()).isEqualTo("#0000FF");
        assertThat(breakdown.get(1).get("name").asText()).isEqualTo("Lazer");
        assertThat(breakdown.get(1).get("percentage").decimalValue()).isEqualByComparingTo("20.00");

        // lista de gastos do mês
        assertThat(body.get("expenses")).hasSize(3);
    }

    @Test
    void monthly_exclui_soft_deleted() throws Exception {
        User user = criarUser("+5547620000002");
        Category cat = criarCategoria(user, "Mercado", "#000000");
        criarExpense(user, cat, "vivo", "50", LocalDate.of(2026, 5, 10));
        Expense morto = criarExpense(user, cat, "morto", "200", LocalDate.of(2026, 5, 12));
        morto.setDeletedAt(Instant.now());
        expenseRepository.saveAndFlush(morto);

        MvcResult res = mvc.perform(get("/reports/monthly")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt(user))
                        .param("month", "2026-05"))
                .andReturn();
        JsonNode body = objectMapper.readTree(res.getResponse().getContentAsString());

        assertThat(body.get("total").decimalValue()).isEqualByComparingTo("50.00");
        assertThat(body.get("breakdown")).hasSize(1);
        assertThat(body.get("expenses")).hasSize(1);
    }

    @Test
    void monthly_so_inclui_gastos_do_mes() throws Exception {
        User user = criarUser("+5547620000003");
        Category cat = criarCategoria(user, "Mercado", "#000000");
        criarExpense(user, cat, "abril", "10", LocalDate.of(2026, 4, 30));
        criarExpense(user, cat, "maio", "20", LocalDate.of(2026, 5, 1));
        criarExpense(user, cat, "junho", "30", LocalDate.of(2026, 6, 1));

        MvcResult res = mvc.perform(get("/reports/monthly")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt(user))
                        .param("month", "2026-05"))
                .andReturn();
        JsonNode body = objectMapper.readTree(res.getResponse().getContentAsString());

        assertThat(body.get("total").decimalValue()).isEqualByComparingTo("20.00");
        assertThat(body.get("expenses")).hasSize(1);
        assertThat(body.get("expenses").get(0).get("description").asText()).isEqualTo("maio");
    }

    @Test
    void monthly_mes_sem_dados_devolve_zero_e_listas_vazias() throws Exception {
        User user = criarUser("+5547620000004");

        MvcResult res = mvc.perform(get("/reports/monthly")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt(user))
                        .param("month", "2026-03"))
                .andReturn();
        JsonNode body = objectMapper.readTree(res.getResponse().getContentAsString());

        assertThat(body.get("total").decimalValue()).isEqualByComparingTo("0");
        assertThat(body.get("breakdown")).isEmpty();
        assertThat(body.get("expenses")).isEmpty();
    }

    @Test
    void monthly_isola_gastos_entre_users() throws Exception {
        User alice = criarUser("+5547620000005");
        User bob = criarUser("+5547620000006");
        Category catAlice = criarCategoria(alice, "Mercado", "#000000");
        Category catBob = criarCategoria(bob, "Lazer", "#FFFFFF");
        criarExpense(alice, catAlice, "alice", "10", LocalDate.of(2026, 5, 10));
        criarExpense(bob, catBob, "bob", "9999", LocalDate.of(2026, 5, 10));

        MvcResult res = mvc.perform(get("/reports/monthly")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt(alice))
                        .param("month", "2026-05"))
                .andReturn();
        JsonNode body = objectMapper.readTree(res.getResponse().getContentAsString());
        assertThat(body.get("total").decimalValue()).isEqualByComparingTo("10.00");
        assertThat(body.get("breakdown")).hasSize(1);
    }

    @Test
    void monthly_sem_param_usa_mes_corrente() throws Exception {
        User user = criarUser("+5547620000007");

        MvcResult res = mvc.perform(get("/reports/monthly")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt(user)))
                .andReturn();
        assertThat(res.getResponse().getStatus()).isEqualTo(200);
        // só checa formato YYYY-MM
        JsonNode body = objectMapper.readTree(res.getResponse().getContentAsString());
        assertThat(body.get("month").asText()).matches("\\d{4}-\\d{2}");
    }

    @Test
    void monthly_com_mes_invalido_retorna_400() throws Exception {
        User user = criarUser("+5547620000008");

        MvcResult res = mvc.perform(get("/reports/monthly")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt(user))
                        .param("month", "lixo"))
                .andReturn();
        assertThat(res.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void categories_devolve_breakdown_no_periodo() throws Exception {
        User user = criarUser("+5547620000009");
        Category mercado = criarCategoria(user, "Mercado", "#000000");
        Category lazer = criarCategoria(user, "Lazer", "#FFFFFF");
        criarExpense(user, mercado, "g1", "200", LocalDate.of(2026, 5, 5));
        criarExpense(user, lazer, "g2", "300", LocalDate.of(2026, 5, 8));
        criarExpense(user, mercado, "g3", "100", LocalDate.of(2026, 6, 1)); // fora do range

        MvcResult res = mvc.perform(get("/reports/categories")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt(user))
                        .param("from", "2026-05-01")
                        .param("to", "2026-05-31"))
                .andReturn();
        JsonNode body = objectMapper.readTree(res.getResponse().getContentAsString());

        assertThat(body.get("total").decimalValue()).isEqualByComparingTo("500.00");
        JsonNode breakdown = body.get("breakdown");
        assertThat(breakdown).hasSize(2);
        // Lazer (300) primeiro, Mercado (200) depois
        assertThat(breakdown.get(0).get("name").asText()).isEqualTo("Lazer");
        assertThat(breakdown.get(1).get("name").asText()).isEqualTo("Mercado");
    }

    @Test
    void categories_from_apos_to_retorna_400() throws Exception {
        User user = criarUser("+5547620000010");
        MvcResult res = mvc.perform(get("/reports/categories")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt(user))
                        .param("from", "2026-06-01")
                        .param("to", "2026-05-01"))
                .andReturn();
        assertThat(res.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void categories_sem_params_retorna_400() throws Exception {
        User user = criarUser("+5547620000011");
        MvcResult res = mvc.perform(get("/reports/categories")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt(user)))
                .andReturn();
        assertThat(res.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void categories_sem_jwt_retorna_401() throws Exception {
        MvcResult res = mvc.perform(get("/reports/categories")
                        .param("from", "2026-05-01").param("to", "2026-05-31"))
                .andReturn();
        assertThat(res.getResponse().getStatus()).isEqualTo(401);
    }

    // ----------------------------------------------------------------

    private String jwt(User u) {
        return jwtService.issueAccessToken(u.getId(), u.getPhone()).token();
    }

    private User criarUser(String phone) {
        return userRepository.findByPhone(phone).orElseGet(() -> {
            User u = new User();
            u.setName("Test " + phone);
            u.setPhone(phone);
            Instant now = Instant.now();
            u.setCreatedAt(now);
            u.setUpdatedAt(now);
            return userRepository.save(u);
        });
    }

    private Category criarCategoria(User owner, String name, String color) {
        Category c = new Category();
        c.setUserId(owner.getId());
        c.setName(name);
        c.setIcon("circle");
        c.setColor(color);
        c.setActive(true);
        Instant now = Instant.now();
        c.setCreatedAt(now);
        c.setUpdatedAt(now);
        return categoryRepository.save(c);
    }

    private Expense criarExpense(User owner, Category cat, String description,
                                 String amount, LocalDate occurredAt) {
        Expense e = new Expense();
        e.setUserId(owner.getId());
        e.setCategoryId(cat.getId());
        e.setDescription(description);
        e.setAmount(new BigDecimal(amount));
        e.setOccurredAt(occurredAt);
        e.setSource(ExpenseSource.WEB);
        Instant now = Instant.now();
        e.setCreatedAt(now);
        e.setUpdatedAt(now);
        return expenseRepository.save(e);
    }

    static UUID anyUuid() { return UUID.randomUUID(); }
}

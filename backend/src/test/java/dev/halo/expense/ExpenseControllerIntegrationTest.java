package dev.halo.expense;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.halo.auth.JwtService;
import dev.halo.category.Category;
import dev.halo.category.CategoryRepository;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integração do {@link ExpenseController} com JWT real, Postgres real e
 * dois usuários — exercita ownership, soft delete, filtros e paginação
 * de ponta a ponta.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ExpenseControllerIntegrationTest {

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
    void post_expenses_cria_gasto_com_source_web() throws Exception {
        User user = criarUser("+5547610000001");
        Category cat = criarCategoria(user, "Mercado");
        String token = jwt(user);

        MvcResult result = mvc.perform(post("/expenses")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "description":"Compras",
                                  "amount":150.50,
                                  "categoryId":"%s",
                                  "occurredAt":"2026-05-10" }
                                """.formatted(cat.getId())))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("description").asText()).isEqualTo("Compras");
        assertThat(body.get("amount").decimalValue()).isEqualByComparingTo("150.50");
        assertThat(body.get("source").asText()).isEqualTo("WEB");
        assertThat(body.get("occurredAt").asText()).isEqualTo("2026-05-10");
    }

    @Test
    void post_expenses_rejeita_amount_zero_ou_negativo() throws Exception {
        User user = criarUser("+5547610000002");
        Category cat = criarCategoria(user, "Mercado");
        String token = jwt(user);

        for (String value : new String[]{"0", "-5"}) {
            MvcResult res = mvc.perform(post("/expenses")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    { "description":"x", "amount":%s,
                                      "categoryId":"%s", "occurredAt":"2026-05-10" }
                                    """.formatted(value, cat.getId())))
                    .andReturn();
            assertThat(res.getResponse().getStatus())
                    .as("amount=%s deveria ser 400", value)
                    .isEqualTo(400);
        }
    }

    @Test
    void post_expenses_rejeita_categoria_de_outro_user() throws Exception {
        User alice = criarUser("+5547610000003");
        User bob = criarUser("+5547610000004");
        Category catBob = criarCategoria(bob, "Lazer");
        String tokenAlice = jwt(alice);

        MvcResult res = mvc.perform(post("/expenses")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAlice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "description":"Festa","amount":50,
                                  "categoryId":"%s","occurredAt":"2026-05-10" }
                                """.formatted(catBob.getId())))
                .andReturn();
        assertThat(res.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void post_expenses_sem_jwt_retorna_401() throws Exception {
        MvcResult res = mvc.perform(post("/expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "description":"x","amount":10,
                                  "categoryId":"%s","occurredAt":"2026-05-10" }
                                """.formatted(UUID.randomUUID())))
                .andReturn();
        assertThat(res.getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void get_expenses_lista_paginado_e_ordenado_desc_por_occurred_at() throws Exception {
        User user = criarUser("+5547610000005");
        Category cat = criarCategoria(user, "Mercado");
        criarExpense(user, cat, "antigo", "10", LocalDate.of(2026, 4, 1));
        criarExpense(user, cat, "meio", "20", LocalDate.of(2026, 4, 15));
        criarExpense(user, cat, "novo", "30", LocalDate.of(2026, 5, 1));
        String token = jwt(user);

        MvcResult res = mvc.perform(get("/expenses")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andReturn();
        assertThat(res.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = objectMapper.readTree(res.getResponse().getContentAsString());
        assertThat(body.get("total").asInt()).isEqualTo(3);
        assertThat(body.get("content").get(0).get("description").asText()).isEqualTo("novo");
        assertThat(body.get("content").get(2).get("description").asText()).isEqualTo("antigo");
    }

    @Test
    void get_expenses_filtra_por_from_to_categoria_e_busca() throws Exception {
        User user = criarUser("+5547610000006");
        Category mercado = criarCategoria(user, "Mercado");
        Category lazer = criarCategoria(user, "Lazer");
        criarExpense(user, mercado, "feira do bairro", "30", LocalDate.of(2026, 4, 1));
        criarExpense(user, mercado, "supermercado central", "100", LocalDate.of(2026, 5, 1));
        criarExpense(user, lazer, "cinema", "40", LocalDate.of(2026, 5, 1));
        String token = jwt(user);

        // from + to + categoria + q juntos
        MvcResult res = mvc.perform(get("/expenses")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("from", "2026-04-15")
                        .param("to", "2026-05-31")
                        .param("category_id", mercado.getId().toString())
                        .param("q", "Super"))
                .andReturn();
        JsonNode body = objectMapper.readTree(res.getResponse().getContentAsString());
        assertThat(body.get("total").asInt()).isEqualTo(1);
        assertThat(body.get("content").get(0).get("description").asText())
                .isEqualTo("supermercado central");
    }

    @Test
    void get_expenses_so_devolve_gastos_do_proprio_user() throws Exception {
        User alice = criarUser("+5547610000007");
        User bob = criarUser("+5547610000008");
        Category catAlice = criarCategoria(alice, "Mercado");
        Category catBob = criarCategoria(bob, "Lazer");
        criarExpense(alice, catAlice, "alice", "10", LocalDate.of(2026, 5, 1));
        criarExpense(bob, catBob, "bob", "10", LocalDate.of(2026, 5, 1));

        MvcResult res = mvc.perform(get("/expenses")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt(alice)))
                .andReturn();
        JsonNode body = objectMapper.readTree(res.getResponse().getContentAsString());
        assertThat(body.get("total").asInt()).isEqualTo(1);
        assertThat(body.get("content").get(0).get("description").asText()).isEqualTo("alice");
    }

    @Test
    void get_expenses_paginacao_respeita_page_e_size() throws Exception {
        User user = criarUser("+5547610000009");
        Category cat = criarCategoria(user, "Mercado");
        for (int i = 0; i < 5; i++) {
            criarExpense(user, cat, "g" + i, "10", LocalDate.of(2026, 4, 1).plusDays(i));
        }
        String token = jwt(user);

        MvcResult page0 = mvc.perform(get("/expenses")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("page", "0").param("size", "2"))
                .andReturn();
        JsonNode body0 = objectMapper.readTree(page0.getResponse().getContentAsString());
        assertThat(body0.get("total").asInt()).isEqualTo(5);
        assertThat(body0.get("content")).hasSize(2);
        assertThat(body0.get("content").get(0).get("description").asText()).isEqualTo("g4");

        MvcResult page1 = mvc.perform(get("/expenses")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("page", "1").param("size", "2"))
                .andReturn();
        JsonNode body1 = objectMapper.readTree(page1.getResponse().getContentAsString());
        assertThat(body1.get("content")).hasSize(2);
        assertThat(body1.get("content").get(0).get("description").asText()).isEqualTo("g2");
    }

    @Test
    void get_expense_por_id_devolve_404_quando_pertence_a_outro_user() throws Exception {
        User alice = criarUser("+5547610000010");
        User bob = criarUser("+5547610000011");
        Category catBob = criarCategoria(bob, "Lazer");
        Expense expBob = criarExpense(bob, catBob, "bob", "10", LocalDate.of(2026, 5, 1));

        MvcResult res = mvc.perform(get("/expenses/" + expBob.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt(alice)))
                .andReturn();
        assertThat(res.getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void patch_expense_atualiza_apenas_os_campos_enviados() throws Exception {
        User user = criarUser("+5547610000012");
        Category mercado = criarCategoria(user, "Mercado");
        Category lazer = criarCategoria(user, "Lazer");
        Expense exp = criarExpense(user, mercado, "antes", "10", LocalDate.of(2026, 5, 1));
        String token = jwt(user);

        mvc.perform(patch("/expenses/" + exp.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "description":"depois", "categoryId":"%s" }
                                """.formatted(lazer.getId())))
                .andReturn();

        Expense persisted = expenseRepository.findById(exp.getId()).orElseThrow();
        assertThat(persisted.getDescription()).isEqualTo("depois");
        assertThat(persisted.getCategoryId()).isEqualTo(lazer.getId());
        // amount e occurredAt preservados
        assertThat(persisted.getAmount()).isEqualByComparingTo("10");
        assertThat(persisted.getOccurredAt()).isEqualTo(LocalDate.of(2026, 5, 1));
    }

    @Test
    void patch_expense_de_outro_user_retorna_404() throws Exception {
        User alice = criarUser("+5547610000013");
        User bob = criarUser("+5547610000014");
        Category catBob = criarCategoria(bob, "Lazer");
        Expense expBob = criarExpense(bob, catBob, "bob", "10", LocalDate.of(2026, 5, 1));

        MvcResult res = mvc.perform(patch("/expenses/" + expBob.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt(alice))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"description\":\"hack\" }"))
                .andReturn();
        assertThat(res.getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void delete_expense_marca_deleted_at_e_some_do_list() throws Exception {
        User user = criarUser("+5547610000015");
        Category cat = criarCategoria(user, "Mercado");
        Expense exp = criarExpense(user, cat, "del", "10", LocalDate.of(2026, 5, 1));
        String token = jwt(user);

        MvcResult resDelete = mvc.perform(delete("/expenses/" + exp.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andReturn();
        assertThat(resDelete.getResponse().getStatus()).isEqualTo(204);

        Expense persisted = expenseRepository.findById(exp.getId()).orElseThrow();
        assertThat(persisted.getDeletedAt()).isNotNull();

        // GET /expenses não devolve mais
        MvcResult resList = mvc.perform(get("/expenses")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andReturn();
        JsonNode body = objectMapper.readTree(resList.getResponse().getContentAsString());
        assertThat(body.get("total").asInt()).isZero();

        // GET /expenses/{id} também devolve 404
        MvcResult resOne = mvc.perform(get("/expenses/" + exp.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andReturn();
        assertThat(resOne.getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void delete_expense_de_outro_user_retorna_404() throws Exception {
        User alice = criarUser("+5547610000016");
        User bob = criarUser("+5547610000017");
        Category catBob = criarCategoria(bob, "Lazer");
        Expense expBob = criarExpense(bob, catBob, "bob", "10", LocalDate.of(2026, 5, 1));

        MvcResult res = mvc.perform(delete("/expenses/" + expBob.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt(alice)))
                .andReturn();
        assertThat(res.getResponse().getStatus()).isEqualTo(404);
        // permanece intacto
        Expense persisted = expenseRepository.findById(expBob.getId()).orElseThrow();
        assertThat(persisted.getDeletedAt()).isNull();
    }

    // ----------------------------------------------------------------

    private String jwt(User user) {
        return jwtService.issueAccessToken(user.getId(), user.getPhone()).token();
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

    private Category criarCategoria(User owner, String name) {
        Category c = new Category();
        c.setUserId(owner.getId());
        c.setName(name);
        c.setIcon("circle");
        c.setColor("#000000");
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
}

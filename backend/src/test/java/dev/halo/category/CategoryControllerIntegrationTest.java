package dev.halo.category;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.halo.auth.JwtService;
import dev.halo.user.User;
import dev.halo.user.UserRepository;
import java.time.Instant;
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
 * Integração do {@link CategoryController} (RF-07, RF-08 — T-034 + T-035).
 *
 * Cobre: listagem com filtragem de globais sobrescritas, criação de cópia
 * via {@code POST /from-global}, idempotência, PATCH/DELETE em cópias.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class CategoryControllerIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired private MockMvc mvc;
    @Autowired private UserRepository userRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private CategoryGlobalRepository categoryGlobalRepository;
    @Autowired private JwtService jwtService;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void get_sem_jwt_retorna_401() throws Exception {
        MvcResult res = mvc.perform(get("/categories")).andReturn();
        assertThat(res.getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void get_lista_todas_as_globais_quando_user_nao_tem_customizacao() throws Exception {
        User user = criarUser("+5547310000001");
        long totalGlobais = categoryGlobalRepository.count();

        MvcResult res = mvc.perform(get("/categories")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt(user)))
                .andReturn();
        JsonNode body = objectMapper.readTree(res.getResponse().getContentAsString());
        assertThat(body.size()).isEqualTo((int) totalGlobais);
        for (int i = 0; i < body.size(); i++) {
            assertThat(body.get(i).get("isCustom").asBoolean()).isFalse();
        }
    }

    @Test
    void get_esconde_global_quando_user_tem_copia() throws Exception {
        User user = criarUser("+5547310000002");
        CategoryGlobal mercado = globalByName("Mercado");
        long totalGlobais = categoryGlobalRepository.count();

        // cria cópia explicitamente via from-global
        mvc.perform(post("/categories/from-global/" + mercado.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt(user)))
                .andReturn();

        MvcResult res = mvc.perform(get("/categories")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt(user)))
                .andReturn();
        JsonNode body = objectMapper.readTree(res.getResponse().getContentAsString());
        // total = (globais - 1) + 1 cópia = total inicial
        assertThat(body.size()).isEqualTo((int) totalGlobais);

        // Mercado aparece exatamente uma vez, e é a cópia (isCustom=true)
        long mercadoCount = 0;
        boolean encontrouCustom = false;
        for (int i = 0; i < body.size(); i++) {
            if ("Mercado".equals(body.get(i).get("name").asText())) {
                mercadoCount++;
                if (body.get(i).get("isCustom").asBoolean()) encontrouCustom = true;
            }
        }
        assertThat(mercadoCount).isEqualTo(1);
        assertThat(encontrouCustom).isTrue();
    }

    @Test
    void from_global_cria_copia_201_inheriting_icon_e_color() throws Exception {
        User user = criarUser("+5547310000003");
        CategoryGlobal mercado = globalByName("Mercado");

        MvcResult res = mvc.perform(post("/categories/from-global/" + mercado.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt(user)))
                .andReturn();

        assertThat(res.getResponse().getStatus()).isEqualTo(201);
        JsonNode body = objectMapper.readTree(res.getResponse().getContentAsString());
        assertThat(body.get("isCustom").asBoolean()).isTrue();
        assertThat(body.get("globalId").asText()).isEqualTo(mercado.getId().toString());
        assertThat(body.get("name").asText()).isEqualTo("Mercado");
        assertThat(body.get("icon").asText()).isEqualTo(mercado.getIcon());
        assertThat(body.get("color").asText()).isEqualTo(mercado.getColor());
    }

    @Test
    void from_global_aceita_overrides_de_icon_e_color() throws Exception {
        User user = criarUser("+5547310000004");
        CategoryGlobal mercado = globalByName("Mercado");

        MvcResult res = mvc.perform(post("/categories/from-global/" + mercado.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"icon\":\"basket\",\"color\":\"#123456\"}"))
                .andReturn();

        assertThat(res.getResponse().getStatus()).isEqualTo(201);
        JsonNode body = objectMapper.readTree(res.getResponse().getContentAsString());
        assertThat(body.get("icon").asText()).isEqualTo("basket");
        assertThat(body.get("color").asText()).isEqualTo("#123456");
    }

    @Test
    void from_global_e_idempotente() throws Exception {
        User user = criarUser("+5547310000005");
        CategoryGlobal mercado = globalByName("Mercado");
        String token = jwt(user);

        MvcResult first = mvc.perform(post("/categories/from-global/" + mercado.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andReturn();
        JsonNode firstBody = objectMapper.readTree(first.getResponse().getContentAsString());
        UUID firstId = UUID.fromString(firstBody.get("id").asText());

        MvcResult second = mvc.perform(post("/categories/from-global/" + mercado.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andReturn();
        assertThat(second.getResponse().getStatus()).isEqualTo(200);
        JsonNode secondBody = objectMapper.readTree(second.getResponse().getContentAsString());
        assertThat(UUID.fromString(secondBody.get("id").asText())).isEqualTo(firstId);
    }

    @Test
    void from_global_404_se_global_nao_existe() throws Exception {
        User user = criarUser("+5547310000006");

        MvcResult res = mvc.perform(post("/categories/from-global/" + UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt(user)))
                .andReturn();
        assertThat(res.getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void from_global_sem_jwt_retorna_401() throws Exception {
        MvcResult res = mvc.perform(post("/categories/from-global/" + UUID.randomUUID()))
                .andReturn();
        assertThat(res.getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void patch_em_copia_de_global_permite_editar_icon_e_color() throws Exception {
        User user = criarUser("+5547310000007");
        CategoryGlobal mercado = globalByName("Mercado");
        String token = jwt(user);

        MvcResult createRes = mvc.perform(post("/categories/from-global/" + mercado.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andReturn();
        UUID copyId = UUID.fromString(
                objectMapper.readTree(createRes.getResponse().getContentAsString())
                        .get("id").asText());

        MvcResult patchRes = mvc.perform(patch("/categories/" + copyId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Mercado\",\"icon\":\"basket\",\"color\":\"#ABCDEF\"}"))
                .andReturn();

        assertThat(patchRes.getResponse().getStatus()).isEqualTo(200);
        Category persisted = categoryRepository.findById(copyId).orElseThrow();
        assertThat(persisted.getIcon()).isEqualTo("basket");
        assertThat(persisted.getColor()).isEqualTo("#ABCDEF");
        assertThat(persisted.getGlobalId()).isEqualTo(mercado.getId());
    }

    @Test
    void delete_em_copia_de_global_desativa_a_copia_e_reexpoe_o_global_no_get() throws Exception {
        User user = criarUser("+5547310000008");
        CategoryGlobal mercado = globalByName("Mercado");
        String token = jwt(user);

        MvcResult createRes = mvc.perform(post("/categories/from-global/" + mercado.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andReturn();
        UUID copyId = UUID.fromString(
                objectMapper.readTree(createRes.getResponse().getContentAsString())
                        .get("id").asText());

        MvcResult delRes = mvc.perform(delete("/categories/" + copyId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andReturn();
        assertThat(delRes.getResponse().getStatus()).isEqualTo(204);

        // GET deve voltar a expor a global Mercado (cópia desativada não esconde mais)
        MvcResult getRes = mvc.perform(get("/categories")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andReturn();
        JsonNode body = objectMapper.readTree(getRes.getResponse().getContentAsString());
        boolean mercadoComoGlobal = false;
        for (int i = 0; i < body.size(); i++) {
            if ("Mercado".equals(body.get(i).get("name").asText())
                    && !body.get(i).get("isCustom").asBoolean()) {
                mercadoComoGlobal = true;
                break;
            }
        }
        assertThat(mercadoComoGlobal).isTrue();
    }

    @Test
    void patch_em_categoria_de_outro_user_retorna_404() throws Exception {
        User alice = criarUser("+5547310000009");
        User bob = criarUser("+5547310000010");
        Category catBob = new Category();
        catBob.setUserId(bob.getId());
        catBob.setName("Lazer");
        catBob.setIcon("sparkles");
        catBob.setColor("#000000");
        catBob.setActive(true);
        catBob.setCreatedAt(Instant.now());
        catBob.setUpdatedAt(Instant.now());
        catBob = categoryRepository.save(catBob);

        MvcResult res = mvc.perform(patch("/categories/" + catBob.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt(alice))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"hack\",\"icon\":\"x\",\"color\":\"#000000\"}"))
                .andReturn();
        assertThat(res.getResponse().getStatus()).isEqualTo(404);
    }

    // -----------------------------------------------------------------

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

    private CategoryGlobal globalByName(String name) {
        return categoryGlobalRepository.findByNameIgnoreCase(name).orElseThrow();
    }
}

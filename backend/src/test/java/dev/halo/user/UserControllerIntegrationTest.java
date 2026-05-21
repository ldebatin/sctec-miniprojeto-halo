package dev.halo.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.halo.auth.JwtService;
import java.time.Instant;
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
 * Integração de {@code /me} com JWT real emitido pelo {@link JwtService}
 * — exercita o {@code JwtAuthenticationFilter} + {@code @AuthenticationPrincipal}
 * de ponta a ponta.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class UserControllerIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void get_me_sem_jwt_retorna_401() throws Exception {
        MvcResult result = mvc.perform(get("/me")).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void get_me_com_jwt_valido_retorna_200_e_perfil() throws Exception {
        User user = criarUser("Carla", "+5547900000001");
        String token = jwtService.issueAccessToken(user.getId(), user.getPhone()).token();

        MvcResult result = mvc.perform(get("/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("id").asText()).isEqualTo(user.getId().toString());
        assertThat(body.get("name").asText()).isEqualTo("Carla");
        assertThat(body.get("phone").asText()).isEqualTo("+5547900000001");
        assertThat(body.get("createdAt").asText()).isNotEmpty();
    }

    @Test
    void patch_me_atualiza_name_no_banco() throws Exception {
        User user = criarUser("Carla", "+5547900000002");
        String token = jwtService.issueAccessToken(user.getId(), user.getPhone()).token();

        MvcResult result = mvc.perform(patch("/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Carla Silva\"}"))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        User persisted = userRepository.findById(user.getId()).orElseThrow();
        assertThat(persisted.getName()).isEqualTo("Carla Silva");
        assertThat(persisted.getPhone()).isEqualTo("+5547900000002");
    }

    @Test
    void patch_me_ignora_phone_no_body() throws Exception {
        User user = criarUser("Carla", "+5547900000003");
        String token = jwtService.issueAccessToken(user.getId(), user.getPhone()).token();

        mvc.perform(patch("/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Nova\",\"phone\":\"+5500000000000\"}"))
                .andReturn();

        User persisted = userRepository.findById(user.getId()).orElseThrow();
        assertThat(persisted.getName()).isEqualTo("Nova");
        assertThat(persisted.getPhone()).isEqualTo("+5547900000003");
    }

    @Test
    void patch_me_com_name_curto_retorna_400() throws Exception {
        User user = criarUser("Carla", "+5547900000004");
        String token = jwtService.issueAccessToken(user.getId(), user.getPhone()).token();

        MvcResult result = mvc.perform(patch("/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"A\"}"))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        // banco não mudou
        User persisted = userRepository.findById(user.getId()).orElseThrow();
        assertThat(persisted.getName()).isEqualTo("Carla");
    }

    private User criarUser(String name, String phone) {
        return userRepository.findByPhone(phone).orElseGet(() -> {
            User user = new User();
            user.setName(name);
            user.setPhone(phone);
            Instant now = Instant.now();
            user.setCreatedAt(now);
            user.setUpdatedAt(now);
            return userRepository.save(user);
        });
    }
}

package dev.halo.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.halo.user.User;
import dev.halo.user.UserRepository;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.List;
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
 * Integração do {@code POST /auth/refresh} com banco real: emite um refresh,
 * rotaciona, e verifica que o antigo foi revogado.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AuthControllerIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void refresh_rotaciona_token_e_revoga_anterior() throws Exception {
        User user = criarUser("+5547800000001");
        RefreshTokenService.IssuedRefreshToken issued =
                refreshTokenService.issue(user.getId(), "Mozilla/5.0", "127.0.0.1");
        String anteriorHash = RefreshTokenService.sha256Hex(issued.token());

        MvcResult result = mvc.perform(post("/auth/refresh")
                        .cookie(new Cookie("refresh_token", issued.token())))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        // novo cookie definido na resposta
        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).contains("refresh_token=").contains("HttpOnly").contains("SameSite=Strict");
        // body contém novo access token
        OtpController.VerifyResponse body =
                objectMapper.readValue(result.getResponse().getContentAsString(),
                        OtpController.VerifyResponse.class);
        assertThat(body.accessToken()).isNotBlank();
        assertThat(body.expiresIn()).isPositive();
        // anterior foi revogado
        RefreshToken anterior = refreshTokenRepository.findByTokenHash(anteriorHash).orElseThrow();
        assertThat(anterior.getRevokedAt()).isNotNull();
    }

    @Test
    void refresh_de_token_ja_rotacionado_retorna_401() throws Exception {
        User user = criarUser("+5547800000002");
        RefreshTokenService.IssuedRefreshToken issued =
                refreshTokenService.issue(user.getId(), "ua", "ip");

        // primeira rotação: ok
        mvc.perform(post("/auth/refresh")
                        .cookie(new Cookie("refresh_token", issued.token())))
                .andReturn();

        // segunda rotação com o token agora revogado: 401
        MvcResult second = mvc.perform(post("/auth/refresh")
                        .cookie(new Cookie("refresh_token", issued.token())))
                .andReturn();

        assertThat(second.getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void refresh_sem_cookie_retorna_401() throws Exception {
        MvcResult result = mvc.perform(post("/auth/refresh"))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void refresh_com_cookie_invalido_retorna_401() throws Exception {
        MvcResult result = mvc.perform(post("/auth/refresh")
                        .cookie(new Cookie("refresh_token", "totalmente-fake")))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void refresh_de_token_expirado_retorna_401() throws Exception {
        User user = criarUser("+5547800000003");
        RefreshTokenService.IssuedRefreshToken issued =
                refreshTokenService.issue(user.getId(), "ua", "ip");

        // força expiração rebobinando expires_at para o passado
        List<RefreshToken> tokens = refreshTokenRepository.findAll();
        for (RefreshToken t : tokens) {
            if (t.getUserId().equals(user.getId())) {
                t.setExpiresAt(Instant.now().minusSeconds(1));
                refreshTokenRepository.saveAndFlush(t);
            }
        }

        MvcResult result = mvc.perform(post("/auth/refresh")
                        .cookie(new Cookie("refresh_token", issued.token())))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(401);
    }

    // --------------------------------------------------------------------
    // DELETE /auth/sessions/current — logout (T-022)
    // --------------------------------------------------------------------

    @Test
    void logout_revoga_refresh_e_proxima_chamada_retorna_401() throws Exception {
        User user = criarUser("+5547800000010");
        RefreshTokenService.IssuedRefreshToken issued =
                refreshTokenService.issue(user.getId(), "ua", "ip");
        String hash = RefreshTokenService.sha256Hex(issued.token());

        MvcResult logout = mvc.perform(delete("/auth/sessions/current")
                        .cookie(new Cookie("refresh_token", issued.token())))
                .andReturn();

        assertThat(logout.getResponse().getStatus()).isEqualTo(204);
        // cookie de limpeza: Max-Age=0, valor vazio
        String setCookie = logout.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).contains("refresh_token=").contains("Max-Age=0");
        // revoked_at foi setado
        RefreshToken persisted = refreshTokenRepository.findByTokenHash(hash).orElseThrow();
        assertThat(persisted.getRevokedAt()).isNotNull();

        // próxima chamada com o mesmo refresh deve retornar 401
        MvcResult retryRefresh = mvc.perform(post("/auth/refresh")
                        .cookie(new Cookie("refresh_token", issued.token())))
                .andReturn();
        assertThat(retryRefresh.getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void logout_e_idempotente_para_cookie_ja_revogado() throws Exception {
        User user = criarUser("+5547800000011");
        RefreshTokenService.IssuedRefreshToken issued =
                refreshTokenService.issue(user.getId(), "ua", "ip");

        // primeira chamada: 204
        mvc.perform(delete("/auth/sessions/current")
                        .cookie(new Cookie("refresh_token", issued.token())))
                .andReturn();

        // segunda chamada (token já revogado): ainda 204
        MvcResult second = mvc.perform(delete("/auth/sessions/current")
                        .cookie(new Cookie("refresh_token", issued.token())))
                .andReturn();
        assertThat(second.getResponse().getStatus()).isEqualTo(204);
    }

    @Test
    void logout_sem_cookie_retorna_204() throws Exception {
        MvcResult result = mvc.perform(delete("/auth/sessions/current")).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(204);
    }

    @Test
    void rota_protegida_via_mockmvc_sem_jwt_retorna_401() throws Exception {
        MvcResult result = mvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/categories")
                        .accept(MediaType.APPLICATION_JSON))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(401);
    }

    private User criarUser(String phone) {
        return userRepository.findByPhone(phone).orElseGet(() -> {
            User u = new User();
            u.setName("Test");
            u.setPhone(phone);
            Instant now = Instant.now();
            u.setCreatedAt(now);
            u.setUpdatedAt(now);
            return userRepository.save(u);
        });
    }
}

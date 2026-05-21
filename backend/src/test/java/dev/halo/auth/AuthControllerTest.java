package dev.halo.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.halo.user.User;
import dev.halo.user.UserRepository;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Testes do {@link AuthController} via MockMvc standalone — cobre o
 * {@code POST /auth/refresh}: caminho feliz com cookie, e 401 para
 * cookie ausente / inválido / expirado / revogado.
 */
class AuthControllerTest {

    private MockMvc mvc;
    private RefreshTokenService refreshTokenService;
    private JwtService jwtService;
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        refreshTokenService = mock(RefreshTokenService.class);
        jwtService = mock(JwtService.class);
        userRepository = mock(UserRepository.class);
        mvc = MockMvcBuilders
                .standaloneSetup(new AuthController(refreshTokenService, jwtService, userRepository))
                .build();
    }

    @Test
    void refresh_retorna_200_com_novo_access_e_cookie_rotacionado() throws Exception {
        UUID userId = UUID.randomUUID();
        User user = userCom(userId);
        RefreshTokenService.IssuedRefreshToken novoRefresh =
                new RefreshTokenService.IssuedRefreshToken("novo-refresh", Duration.ofDays(30));
        when(refreshTokenService.rotate(eq("antigo-refresh"), any(), any()))
                .thenReturn(new RefreshTokenService.RotatedRefreshToken(userId, novoRefresh));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(jwtService.issueAccessToken(eq(userId), eq("+5547999999999")))
                .thenReturn(new JwtService.IssuedAccessToken("novo-jwt", Duration.ofMinutes(15)));

        mvc.perform(post("/auth/refresh")
                        .cookie(new Cookie("refresh_token", "antigo-refresh")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("novo-jwt"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(cookie().value("refresh_token", "novo-refresh"))
                .andExpect(cookie().httpOnly("refresh_token", true))
                .andExpect(cookie().secure("refresh_token", true))
                .andExpect(cookie().path("refresh_token", "/auth"))
                .andExpect(header().string("Set-Cookie", Matchers.containsString("SameSite=Strict")));
    }

    @Test
    void refresh_retorna_401_sem_cookie() throws Exception {
        when(refreshTokenService.rotate(eq(null), any(), any()))
                .thenThrow(new RefreshTokenException("ausente"));

        mvc.perform(post("/auth/refresh"))
                .andExpect(status().isUnauthorized());

        verify(jwtService, never()).issueAccessToken(any(), any());
    }

    @Test
    void refresh_retorna_401_quando_token_desconhecido() throws Exception {
        when(refreshTokenService.rotate(eq("invalido"), any(), any()))
                .thenThrow(new RefreshTokenException("desconhecido"));

        mvc.perform(post("/auth/refresh")
                        .cookie(new Cookie("refresh_token", "invalido")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refresh_retorna_401_quando_token_revogado() throws Exception {
        when(refreshTokenService.rotate(eq("revogado"), any(), any()))
                .thenThrow(new RefreshTokenException("revogado"));

        mvc.perform(post("/auth/refresh")
                        .cookie(new Cookie("refresh_token", "revogado")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refresh_retorna_401_quando_token_expirado() throws Exception {
        when(refreshTokenService.rotate(eq("expirado"), any(), any()))
                .thenThrow(new RefreshTokenException("expirado"));

        mvc.perform(post("/auth/refresh")
                        .cookie(new Cookie("refresh_token", "expirado")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refresh_retorna_401_quando_user_da_sessao_desapareceu() throws Exception {
        UUID userId = UUID.randomUUID();
        when(refreshTokenService.rotate(eq("ok"), any(), any()))
                .thenReturn(new RefreshTokenService.RotatedRefreshToken(
                        userId,
                        new RefreshTokenService.IssuedRefreshToken("n", Duration.ofDays(30))));
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        mvc.perform(post("/auth/refresh")
                        .cookie(new Cookie("refresh_token", "ok")))
                .andExpect(status().isUnauthorized());

        verify(jwtService, never()).issueAccessToken(any(), any());
    }

    private User userCom(UUID id) {
        User user = new User();
        user.setId(id);
        user.setPhone("+5547999999999");
        user.setName("Carla");
        Instant now = Instant.now();
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        return user;
    }
}

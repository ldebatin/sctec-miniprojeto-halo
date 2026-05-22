package dev.halo.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.halo.user.InvalidPhoneException;
import dev.halo.user.User;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Testes do {@link OtpController} via MockMvc standalone.
 *
 * Verifica os contratos HTTP de {@code POST /auth/otp/request} (T-019) e
 * {@code POST /auth/otp/verify} (T-020): status codes, body e cookie de
 * refresh token.
 */
class OtpControllerTest {

    private MockMvc mvc;
    private OtpService otpService;
    private JwtService jwtService;
    private RefreshTokenService refreshTokenService;

    @BeforeEach
    void setUp() {
        otpService = mock(OtpService.class);
        jwtService = mock(JwtService.class);
        refreshTokenService = mock(RefreshTokenService.class);
        mvc = MockMvcBuilders
                .standaloneSetup(new OtpController(otpService, jwtService, refreshTokenService))
                .build();
    }

    @Test
    void retorna_200_para_telefone_valido() throws Exception {
        doNothing().when(otpService).send(any());

        mvc.perform(post("/auth/otp/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\": \"+5547999999999\"}"))
                .andExpect(status().isOk());

        verify(otpService).send("+5547999999999");
    }

    @Test
    void retorna_200_mesmo_quando_telefone_nao_existe_em_users() throws Exception {
        doNothing().when(otpService).send(any());

        mvc.perform(post("/auth/otp/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\": \"+5547000000000\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void retorna_429_quando_cooldown_ativo() throws Exception {
        doThrow(new OtpCooldownException("+5547999999999"))
                .when(otpService).send(any());

        mvc.perform(post("/auth/otp/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\": \"+5547999999999\"}"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void retorna_400_para_telefone_invalido() throws Exception {
        doThrow(new InvalidPhoneException("telefone inválido"))
                .when(otpService).send(any());

        mvc.perform(post("/auth/otp/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\": \"abc\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void retorna_400_para_body_sem_campo_phone() throws Exception {
        mvc.perform(post("/auth/otp/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verify(otpService, never()).send(any());
    }

    @Test
    void retorna_400_para_phone_em_branco() throws Exception {
        mvc.perform(post("/auth/otp/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\": \"\"}"))
                .andExpect(status().isBadRequest());

        verify(otpService, never()).send(any());
    }

    // --------------------------------------------------------------------
    // verify (T-020)
    // --------------------------------------------------------------------

    @Test
    void verify_retorna_200_com_access_token_e_cookie_refresh() throws Exception {
        User user = userCom();
        when(otpService.verify(eq("+5547999999999"), eq("123456"))).thenReturn(user);
        when(jwtService.issueAccessToken(eq(user.getId()), eq("+5547999999999")))
                .thenReturn(new JwtService.IssuedAccessToken("jwt.payload.sig", Duration.ofMinutes(15)));
        when(refreshTokenService.issue(eq(user.getId()), any(), any()))
                .thenReturn(new RefreshTokenService.IssuedRefreshToken(
                        "refresh-plaintext", Duration.ofDays(30)));

        mvc.perform(post("/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\": \"+5547999999999\", \"code\": \"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("jwt.payload.sig"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.user.id").value(user.getId().toString()))
                .andExpect(jsonPath("$.user.phone").value("+5547999999999"))
                .andExpect(jsonPath("$.user.name").value("Carla"))
                .andExpect(cookie().value(OtpController.REFRESH_COOKIE_NAME, "refresh-plaintext"))
                .andExpect(cookie().httpOnly(OtpController.REFRESH_COOKIE_NAME, true))
                .andExpect(cookie().secure(OtpController.REFRESH_COOKIE_NAME, true))
                .andExpect(cookie().maxAge(OtpController.REFRESH_COOKIE_NAME, (int) Duration.ofDays(30).toSeconds()))
                .andExpect(cookie().path(OtpController.REFRESH_COOKIE_NAME, "/auth"))
                .andExpect(header().string("Set-Cookie", Matchers.containsString("SameSite=Strict")));
    }

    @Test
    void verify_retorna_401_quando_codigo_invalido() throws Exception {
        when(otpService.verify(any(), any()))
                .thenThrow(new OtpVerificationException("código inválido"));

        mvc.perform(post("/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\": \"+5547999999999\", \"code\": \"000000\"}"))
                .andExpect(status().isUnauthorized());

        verify(jwtService, never()).issueAccessToken(any(), any());
        verify(refreshTokenService, never()).issue(any(), any(), any());
    }

    @Test
    void verify_retorna_401_para_telefone_invalido() throws Exception {
        when(otpService.verify(any(), any()))
                .thenThrow(new InvalidPhoneException("telefone inválido"));

        mvc.perform(post("/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\": \"abc\", \"code\": \"123456\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void verify_retorna_400_para_code_fora_de_6_digitos() throws Exception {
        mvc.perform(post("/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\": \"+5547999999999\", \"code\": \"12345\"}"))
                .andExpect(status().isBadRequest());

        verify(otpService, never()).verify(any(), any());
    }

    @Test
    void verify_retorna_400_quando_code_ausente() throws Exception {
        mvc.perform(post("/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\": \"+5547999999999\"}"))
                .andExpect(status().isBadRequest());

        verify(otpService, never()).verify(any(), any());
    }

    private User userCom() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setPhone("+5547999999999");
        user.setName("Carla");
        Instant now = Instant.now();
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        return user;
    }
}

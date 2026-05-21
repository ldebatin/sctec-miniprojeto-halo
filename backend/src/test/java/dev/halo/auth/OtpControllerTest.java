package dev.halo.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.halo.user.InvalidPhoneException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Testes do {@link OtpController} via MockMvc standalone.
 *
 * Verifica os contratos HTTP: status codes e comportamento para cada cenário.
 */
class OtpControllerTest {

    private MockMvc mvc;
    private OtpService otpService;

    @BeforeEach
    void setUp() {
        otpService = mock(OtpService.class);
        mvc = MockMvcBuilders
                .standaloneSetup(new OtpController(otpService))
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
        // O serviço não lança exceção — retorna 200 sem revelar se o telefone existe
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
}

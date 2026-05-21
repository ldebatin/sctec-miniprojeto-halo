package dev.halo.auth;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.halo.user.InvalidPhoneException;
import dev.halo.user.PhoneNumberService;
import dev.halo.whatsapp.EvolutionClient;
import java.security.SecureRandom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Testes unitários do {@link OtpService} com dependências mockadas.
 *
 * Verifica o fluxo de geração, persistência e envio do OTP sem banco de dados.
 */
class OtpServiceUnitTest {

    private OtpCodeRepository otpCodeRepository;
    private EvolutionClient evolutionClient;
    private OtpRateLimiter rateLimiter;
    private OtpService otpService;

    @BeforeEach
    void setUp() {
        otpCodeRepository = mock(OtpCodeRepository.class);
        evolutionClient = mock(EvolutionClient.class);
        rateLimiter = mock(OtpRateLimiter.class);

        when(otpCodeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        otpService = new OtpService(
                otpCodeRepository,
                new PhoneNumberService(),
                evolutionClient,
                rateLimiter,
                new BCryptPasswordEncoder(),
                new SecureRandom()
        );
    }

    @Test
    void envia_otp_quando_cooldown_nao_ativo() {
        when(rateLimiter.tryConsume("+5547999999999")).thenReturn(true);

        otpService.send("+5547999999999");

        verify(otpCodeRepository).save(any(OtpCode.class));
        verify(evolutionClient).sendText(eq("+5547999999999"), any(String.class));
    }

    @Test
    void mensagem_enviada_contem_aviso_de_seguranca() {
        when(rateLimiter.tryConsume("+5547999999999")).thenReturn(true);

        otpService.send("+5547999999999");

        verify(evolutionClient).sendText(
                eq("+5547999999999"),
                contains("Nunca compartilhe este código.")
        );
    }

    @Test
    void lanca_excecao_quando_cooldown_ativo() {
        when(rateLimiter.tryConsume("+5547999999999")).thenReturn(false);

        assertThatThrownBy(() -> otpService.send("+5547999999999"))
                .isInstanceOf(OtpCooldownException.class);

        verify(otpCodeRepository, never()).save(any());
        verify(evolutionClient, never()).sendText(any(), any());
    }

    @Test
    void lanca_excecao_para_telefone_invalido() {
        assertThatThrownBy(() -> otpService.send("abc"))
                .isInstanceOf(InvalidPhoneException.class);

        verify(rateLimiter, never()).tryConsume(any());
        verify(otpCodeRepository, never()).save(any());
        verify(evolutionClient, never()).sendText(any(), any());
    }

    @Test
    void normaliza_telefone_antes_de_verificar_cooldown() {
        when(rateLimiter.tryConsume("+5547999999999")).thenReturn(true);

        // entrada com formatação — deve ser normalizada para E.164
        otpService.send("5547999999999");

        verify(rateLimiter).tryConsume("+5547999999999");
        verify(evolutionClient).sendText(eq("+5547999999999"), any());
    }

    @Test
    void persiste_otp_com_ttl_de_5_minutos() {
        when(rateLimiter.tryConsume("+5547999999999")).thenReturn(true);

        otpService.send("+5547999999999");

        verify(otpCodeRepository).save(any(OtpCode.class));
    }
}

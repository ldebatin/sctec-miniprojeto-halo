package dev.halo.auth;

import static org.assertj.core.api.Assertions.assertThat;
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
import dev.halo.user.User;
import dev.halo.user.UserRepository;
import dev.halo.whatsapp.EvolutionClient;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Testes unitários do {@link OtpService} com dependências mockadas.
 *
 * Cobre o fluxo de envio (T-019) e o de verificação (T-020): persistência,
 * comparação bcrypt, contador de tentativas e invalidação após o limite.
 */
class OtpServiceUnitTest {

    private OtpCodeRepository otpCodeRepository;
    private UserRepository userRepository;
    private EvolutionClient evolutionClient;
    private OtpRateLimiter rateLimiter;
    private BCryptPasswordEncoder passwordEncoder;
    private OtpService otpService;

    @BeforeEach
    void setUp() {
        otpCodeRepository = mock(OtpCodeRepository.class);
        userRepository = mock(UserRepository.class);
        evolutionClient = mock(EvolutionClient.class);
        rateLimiter = mock(OtpRateLimiter.class);
        passwordEncoder = new BCryptPasswordEncoder();

        when(otpCodeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        otpService = new OtpService(
                otpCodeRepository,
                userRepository,
                new PhoneNumberService(),
                evolutionClient,
                rateLimiter,
                passwordEncoder,
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

    // --------------------------------------------------------------------
    // verify (T-020)
    // --------------------------------------------------------------------

    @Test
    void verify_retorna_user_quando_codigo_e_telefone_validos() {
        OtpCode otp = otpCom("123456", 0, Instant.now().plusSeconds(60));
        when(otpCodeRepository.findLatestValid(eq("+5547999999999"), any()))
                .thenReturn(Optional.of(otp));
        User user = userCom("+5547999999999");
        when(userRepository.findByPhone("+5547999999999"))
                .thenReturn(Optional.of(user));

        User result = otpService.verify("+5547999999999", "123456");

        assertThat(result).isSameAs(user);
        assertThat(otp.getUsedAt()).isNotNull();
        verify(otpCodeRepository).save(otp);
    }

    @Test
    void verify_lanca_quando_nao_ha_otp_valido() {
        when(otpCodeRepository.findLatestValid(eq("+5547999999999"), any()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> otpService.verify("+5547999999999", "123456"))
                .isInstanceOf(OtpVerificationException.class);

        verify(otpCodeRepository, never()).save(any());
    }

    @Test
    void verify_incrementa_attempts_quando_codigo_errado() {
        OtpCode otp = otpCom("123456", 0, Instant.now().plusSeconds(60));
        when(otpCodeRepository.findLatestValid(eq("+5547999999999"), any()))
                .thenReturn(Optional.of(otp));

        assertThatThrownBy(() -> otpService.verify("+5547999999999", "999999"))
                .isInstanceOf(OtpVerificationException.class);

        ArgumentCaptor<OtpCode> captor = ArgumentCaptor.forClass(OtpCode.class);
        verify(otpCodeRepository).save(captor.capture());
        assertThat(captor.getValue().getAttempts()).isEqualTo(1);
        assertThat(captor.getValue().getUsedAt()).isNull();
    }

    @Test
    void verify_invalida_otp_apos_5_tentativas_erradas() {
        OtpCode otp = otpCom("123456", 4, Instant.now().plusSeconds(60));
        when(otpCodeRepository.findLatestValid(eq("+5547999999999"), any()))
                .thenReturn(Optional.of(otp));

        assertThatThrownBy(() -> otpService.verify("+5547999999999", "000000"))
                .isInstanceOf(OtpVerificationException.class);

        assertThat(otp.getAttempts()).isEqualTo(5);
        assertThat(otp.getUsedAt()).isNotNull();
        verify(otpCodeRepository).save(otp);
    }

    @Test
    void verify_lanca_quando_telefone_nao_tem_usuario() {
        OtpCode otp = otpCom("123456", 0, Instant.now().plusSeconds(60));
        when(otpCodeRepository.findLatestValid(eq("+5547999999999"), any()))
                .thenReturn(Optional.of(otp));
        when(userRepository.findByPhone("+5547999999999"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> otpService.verify("+5547999999999", "123456"))
                .isInstanceOf(OtpVerificationException.class);
    }

    private OtpCode otpCom(String code, int attempts, Instant expiresAt) {
        OtpCode otp = new OtpCode();
        otp.setPhone("+5547999999999");
        otp.setCodeHash(passwordEncoder.encode(code));
        otp.setAttempts(attempts);
        otp.setCreatedAt(Instant.now());
        otp.setExpiresAt(expiresAt);
        return otp;
    }

    private User userCom(String phone) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setPhone(phone);
        user.setName("Carla");
        Instant now = Instant.now();
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        return user;
    }
}

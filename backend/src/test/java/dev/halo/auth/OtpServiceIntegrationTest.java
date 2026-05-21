package dev.halo.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import dev.halo.whatsapp.EvolutionClient;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Testes de integração do {@link OtpService} com banco PostgreSQL real via Testcontainers.
 *
 * Verifica persistência em {@code otp_codes}, TTL, hash bcrypt e cooldown
 * com o contexto Spring completo. O {@link EvolutionClient} é mockado para
 * evitar chamadas reais ao Evolution Go.
 */
@SpringBootTest
@Testcontainers
@Transactional
class OtpServiceIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private OtpService otpService;

    @Autowired
    private OtpCodeRepository otpCodeRepository;

    @MockBean
    private EvolutionClient evolutionClient;

    @BeforeEach
    void setUp() {
        doNothing().when(evolutionClient).sendText(any(), any());
    }

    @Test
    void persiste_otp_na_tabela_otp_codes() {
        otpService.send("+5547999999999");

        List<OtpCode> otps = otpCodeRepository.findAll()
                .stream()
                .filter(o -> o.getPhone().equals("+5547999999999"))
                .toList();

        assertThat(otps).hasSize(1);
        OtpCode otp = otps.get(0);
        assertThat(otp.getPhone()).isEqualTo("+5547999999999");
        assertThat(otp.getCodeHash()).isNotBlank();
        assertThat(otp.getUsedAt()).isNull();
        assertThat(otp.getAttempts()).isZero();
    }

    @Test
    void otp_tem_ttl_de_5_minutos() {
        Instant before = Instant.now();

        otpService.send("+5547111111111");

        OtpCode otp = otpCodeRepository.findAll()
                .stream()
                .filter(o -> o.getPhone().equals("+5547111111111"))
                .findFirst()
                .orElseThrow();

        Instant expectedExpiry = before.plusSeconds(OtpService.OTP_TTL_MINUTES * 60L);
        assertThat(otp.getExpiresAt()).isAfterOrEqualTo(expectedExpiry.minusSeconds(2));
        assertThat(otp.getExpiresAt()).isBeforeOrEqualTo(expectedExpiry.plusSeconds(2));
    }

    @Test
    void code_hash_e_bcrypt_do_codigo_gerado() {
        otpService.send("+5547222222222");

        OtpCode otp = otpCodeRepository.findAll()
                .stream()
                .filter(o -> o.getPhone().equals("+5547222222222"))
                .findFirst()
                .orElseThrow();

        // Hash bcrypt começa com $2a$ ou $2b$
        assertThat(otp.getCodeHash()).startsWith("$2");
        // Não armazena o código em texto plano
        assertThat(otp.getCodeHash()).doesNotMatch("\\d{6}");
    }

    @Test
    void envia_mensagem_via_evolution_com_aviso_de_seguranca() {
        otpService.send("+5547333333333");

        verify(evolutionClient).sendText(
                eq("+5547333333333"),
                contains("Nunca compartilhe este código.")
        );
    }

    @Test
    void segundo_envio_imediato_lanca_cooldown_exception() {
        otpService.send("+5547444444444");

        assertThatThrownBy(() -> otpService.send("+5547444444444"))
                .isInstanceOf(OtpCooldownException.class);

        long count = otpCodeRepository.findAll()
                .stream()
                .filter(o -> o.getPhone().equals("+5547444444444"))
                .count();
        assertThat(count).isEqualTo(1);
        verify(evolutionClient, times(1)).sendText(eq("+5547444444444"), any());
    }

    @Test
    void telefones_diferentes_podem_solicitar_otp_simultaneamente() {
        otpService.send("+5547555555551");
        otpService.send("+5547555555552");

        long count = otpCodeRepository.findAll()
                .stream()
                .filter(o -> o.getPhone().startsWith("+554755555555"))
                .count();
        assertThat(count).isEqualTo(2);
        verify(evolutionClient, times(2)).sendText(any(), any());
    }

    @Test
    void normaliza_telefone_antes_de_persistir() {
        // Entrada sem o "+" — deve ser normalizada para E.164
        otpService.send("5547666666666");

        OtpCode otp = otpCodeRepository.findAll()
                .stream()
                .filter(o -> o.getPhone().equals("+5547666666666"))
                .findFirst()
                .orElseThrow();

        assertThat(otp.getPhone()).isEqualTo("+5547666666666");
    }
}

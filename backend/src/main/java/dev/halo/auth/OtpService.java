package dev.halo.auth;

import dev.halo.user.InvalidPhoneException;
import dev.halo.user.PhoneNumberService;
import dev.halo.user.User;
import dev.halo.user.UserRepository;
import dev.halo.whatsapp.EvolutionClient;
import java.security.SecureRandom;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Geração, envio e verificação de OTP via WhatsApp (RF-09).
 *
 * Envio ({@link #send}, T-019):
 * <ol>
 *   <li>Normaliza o telefone para E.164.</li>
 *   <li>Verifica cooldown de 60s via {@link OtpRateLimiter} (Bucket4j em memória).</li>
 *   <li>Gera código de 6 dígitos com {@link SecureRandom}.</li>
 *   <li>Persiste hash bcrypt em {@code otp_codes} com TTL de 5 min.</li>
 *   <li>Envia mensagem via {@link EvolutionClient} com aviso de segurança.</li>
 * </ol>
 *
 * Verificação ({@link #verify}, T-020):
 * <ol>
 *   <li>Busca o OTP mais recente ainda válido para o telefone.</li>
 *   <li>Compara o código via bcrypt; em erro, incrementa {@code attempts}
 *       (após 5 tentativas, marca {@code usedAt} e invalida).</li>
 *   <li>Em sucesso, marca {@code usedAt} e devolve o {@code User}.</li>
 * </ol>
 *
 * Emissão de JWT + refresh token e construção da resposta HTTP ficam no
 * {@link OtpController} — este service só valida.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OtpService {

    static final int OTP_TTL_MINUTES = 5;
    static final int OTP_MAX_ATTEMPTS = 5;
    static final String OTP_MESSAGE_TEMPLATE =
            "Seu código de acesso Halo: *%s*\n\nNunca compartilhe este código.";

    private final OtpCodeRepository otpCodeRepository;
    private final UserRepository userRepository;
    private final PhoneNumberService phoneNumberService;
    private final EvolutionClient evolutionClient;
    private final OtpRateLimiter rateLimiter;
    private final BCryptPasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom;

    /**
     * Envia um OTP para o telefone informado.
     *
     * @param rawPhone telefone em qualquer formato aceito pelo {@link PhoneNumberService}
     * @throws InvalidPhoneException  se o telefone não puder ser normalizado
     * @throws OtpCooldownException   se o cooldown de 60s ainda não expirou
     */
    @Transactional
    public void send(String rawPhone) {
        String phone = phoneNumberService.normalize(rawPhone);

        if (!rateLimiter.tryConsume(phone)) {
            log.warn("OTP cooldown ativo phone={}", phone);
            throw new OtpCooldownException(phone);
        }

        String code = generateCode();
        String hash = passwordEncoder.encode(code);

        OtpCode otp = new OtpCode();
        otp.setPhone(phone);
        otp.setCodeHash(hash);
        otp.setAttempts(0);
        Instant now = Instant.now();
        otp.setCreatedAt(now);
        otp.setExpiresAt(now.plusSeconds(OTP_TTL_MINUTES * 60L));

        otpCodeRepository.save(otp);

        String message = String.format(OTP_MESSAGE_TEMPLATE, code);
        evolutionClient.sendText(phone, message);

        log.info("OTP enviado phone={}", phone);
    }

    /**
     * Verifica um código OTP. Em sucesso, marca o registro como consumido
     * ({@code usedAt = now}) e retorna o {@link User} dono do telefone.
     *
     * Em falha (código inexistente, expirado, errado, ou usuário ausente)
     * lança {@link OtpVerificationException}. Cada erro de match incrementa
     * {@code attempts}; após {@link #OTP_MAX_ATTEMPTS} o registro é
     * invalidado ({@code usedAt = now}) mesmo sem sucesso, prevenindo
     * brute force.
     *
     * @param rawPhone telefone em qualquer formato aceito pelo {@link PhoneNumberService}
     * @param code     código de 6 dígitos que o usuário digitou
     */
    @Transactional
    public User verify(String rawPhone, String code) {
        String phone = phoneNumberService.normalize(rawPhone);
        Instant now = Instant.now();

        OtpCode otp = otpCodeRepository.findLatestValid(phone, now)
                .orElseThrow(() -> new OtpVerificationException("nenhum OTP válido"));

        if (!passwordEncoder.matches(code, otp.getCodeHash())) {
            otp.setAttempts(otp.getAttempts() + 1);
            if (otp.getAttempts() >= OTP_MAX_ATTEMPTS) {
                otp.setUsedAt(now);
                log.warn("OTP invalidado por excesso de tentativas phone={}", phone);
            }
            otpCodeRepository.save(otp);
            throw new OtpVerificationException("código inválido");
        }

        otp.setUsedAt(now);
        otpCodeRepository.save(otp);

        User user = userRepository.findByPhone(phone)
                .orElseThrow(() -> new OtpVerificationException("usuário não cadastrado"));

        log.info("OTP verificado userId={} phone={}", user.getId(), phone);
        return user;
    }

    /** Gera um código numérico de 6 dígitos com zeros à esquerda quando necessário. */
    private String generateCode() {
        int number = secureRandom.nextInt(1_000_000);
        return String.format("%06d", number);
    }
}

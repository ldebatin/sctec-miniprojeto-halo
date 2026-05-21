package dev.halo.auth;

import dev.halo.user.InvalidPhoneException;
import dev.halo.user.PhoneNumberService;
import dev.halo.whatsapp.EvolutionClient;
import java.security.SecureRandom;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Geração e envio de OTP via WhatsApp (RF-09).
 *
 * Fluxo:
 * <ol>
 *   <li>Normaliza o telefone para E.164.</li>
 *   <li>Verifica cooldown de 60s via {@link OtpRateLimiter} (Bucket4j em memória).</li>
 *   <li>Gera código de 6 dígitos com {@link SecureRandom}.</li>
 *   <li>Persiste hash bcrypt em {@code otp_codes} com TTL de 5 min.</li>
 *   <li>Envia mensagem via {@link EvolutionClient} com aviso de segurança.</li>
 * </ol>
 *
 * A resposta ao caller é sempre 200 — não revela se o telefone existe em
 * {@code users} ou não (critério RF-09).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OtpService {

    static final int OTP_TTL_MINUTES = 5;
    static final String OTP_MESSAGE_TEMPLATE =
            "Seu código de acesso Halo: *%s*\n\nNunca compartilhe este código.";

    private final OtpCodeRepository otpCodeRepository;
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

    /** Gera um código numérico de 6 dígitos com zeros à esquerda quando necessário. */
    private String generateCode() {
        int number = secureRandom.nextInt(1_000_000);
        return String.format("%06d", number);
    }
}

package dev.halo.auth;

import dev.halo.user.InvalidPhoneException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints de autenticação via OTP (RF-09).
 *
 * {@code POST /auth/otp/send} — gera e envia o código via WhatsApp.
 *
 * A resposta é sempre 200 independente de o telefone existir em {@code users}
 * ou não — não vazar essa informação é requisito de segurança do RF-09.
 *
 * 429 é retornado quando o cooldown de 60s ainda está ativo para o telefone.
 * 400 é retornado para telefone inválido (não normalizável para E.164).
 */
@RestController
@RequestMapping("/auth/otp")
@RequiredArgsConstructor
@Slf4j
public class OtpController {

    private final OtpService otpService;

    @PostMapping("/request")
    public ResponseEntity<Void> send(@Valid @RequestBody SendOtpRequest request) {
        try {
            otpService.send(request.phone());
        } catch (OtpCooldownException e) {
            log.debug("OTP rejeitado por cooldown phone={}", e.getPhone());
            return ResponseEntity.status(429).build();
        } catch (InvalidPhoneException e) {
            log.debug("OTP rejeitado por telefone inválido: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok().build();
    }

    /** Payload de entrada para {@code POST /auth/otp/send}. */
    record SendOtpRequest(@NotBlank String phone) {}
}

package dev.halo.auth;

/**
 * Falha de verificação do OTP (RF-09): código ausente, errado, expirado,
 * já consumido, ou telefone sem usuário cadastrado.
 *
 * Mapeada para HTTP 401 no {@link OtpController}. Sempre genérica para
 * não vazar qual etapa falhou (mesmo critério do {@code /auth/otp/request}).
 */
public class OtpVerificationException extends RuntimeException {
    public OtpVerificationException(String message) {
        super(message);
    }
}

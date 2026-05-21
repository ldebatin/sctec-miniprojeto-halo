package dev.halo.auth;

/**
 * Falha de rotação do refresh token (RF-09, T-021): token ausente, hash
 * desconhecido, expirado, ou já revogado.
 *
 * Mapeada para HTTP 401 no controller. Mensagem deliberadamente genérica
 * para não vazar o motivo da rejeição.
 */
public class RefreshTokenException extends RuntimeException {
    public RefreshTokenException(String message) {
        super(message);
    }
}

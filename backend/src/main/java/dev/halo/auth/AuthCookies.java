package dev.halo.auth;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import org.springframework.http.ResponseCookie;

/**
 * Helpers para construir o cookie {@code refresh_token} e resolver o IP
 * do cliente respeitando proxy reverso (RF-09, T-020/T-021).
 *
 * Centralizado para que {@link OtpController} (emissão inicial) e
 * {@link AuthController} (rotação em {@code /auth/refresh}) não divirjam
 * nos atributos do cookie.
 */
final class AuthCookies {

    static final String REFRESH_COOKIE_NAME = "refresh_token";
    private static final String COOKIE_PATH = "/auth";

    private AuthCookies() {}

    /** Constrói o cookie httpOnly; Secure; SameSite=Strict do refresh token. */
    static ResponseCookie buildRefreshCookie(String value, Duration maxAge) {
        return ResponseCookie.from(REFRESH_COOKIE_NAME, value)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path(COOKIE_PATH)
                .maxAge(maxAge)
                .build();
    }

    /**
     * Cookie de "limpeza" usado no logout (T-022): mesmo nome/path, valor
     * vazio e {@code Max-Age=0} para que o navegador descarte imediatamente.
     */
    static ResponseCookie expiredRefreshCookie() {
        return buildRefreshCookie("", Duration.ZERO);
    }

    /**
     * Resolve o IP do cliente respeitando proxy reverso. Confiamos em
     * {@code X-Forwarded-For} porque o backend só é exposto via
     * Traefik/nginx em produção; em dev/teste o header não existe e
     * usamos o {@code remoteAddr} direto.
     */
    static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        return request.getRemoteAddr();
    }

    /** Lê o valor do cookie {@code refresh_token} da requisição. */
    static String readRefreshCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie c : cookies) {
            if (REFRESH_COOKIE_NAME.equals(c.getName())) {
                return c.getValue();
            }
        }
        return null;
    }
}

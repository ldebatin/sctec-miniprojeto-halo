package dev.halo.common.security;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propriedades de hardening da camada de segurança (analise-tecnica §10.3).
 *
 * {@code corsAllowedOrigins} é a lista de origens autorizadas a chamar a
 * API com credenciais (cookie do refresh token). Default cobre o Vite
 * dev server local; em produção é injetado via env apontando para o
 * domínio do PWA.
 */
@ConfigurationProperties(prefix = "halo.security")
public record SecurityProperties(List<String> corsAllowedOrigins) {}

package dev.halo.auth;

import java.security.SecureRandom;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Beans de infraestrutura para autenticação (RF-09).
 *
 * {@link BCryptPasswordEncoder} é usado para hash do código OTP e,
 * futuramente, para hash de refresh tokens.
 *
 * {@link SecureRandom} é injetado como bean para facilitar testes
 * (pode ser substituído por um mock determinístico).
 */
@Configuration
public class AuthConfig {

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecureRandom secureRandom() {
        return new SecureRandom();
    }
}

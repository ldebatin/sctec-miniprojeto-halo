package dev.halo.common.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                // Webhooks têm autenticação própria por header apikey
                // (ver EvolutionWebhookController).
                .requestMatchers("/webhooks/**").permitAll()
                // Endpoints de autenticação (OTP send/verify, futuramente refresh/logout).
                .requestMatchers("/auth/**").permitAll()
                // /error é dispatch interno do Spring (ex.: forward em 404/405).
                // Sem permitAll aqui, qualquer erro em rota pública dispara
                // o WWW-Authenticate do httpBasic e o navegador pede senha.
                .requestMatchers("/error").permitAll()
                .anyRequest().authenticated()
            )
            .httpBasic(b -> {});
        return http.build();
    }
}

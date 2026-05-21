package dev.halo.common.security;

import dev.halo.auth.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.http.HttpStatus;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Configuração da cadeia de filtros (T-021, RF-09, analise-tecnica §10).
 *
 * <ul>
 *   <li>{@link JwtAuthenticationFilter} antes do
 *       {@link UsernamePasswordAuthenticationFilter} valida o header
 *       {@code Authorization: Bearer ...} e popula o {@code SecurityContext}.</li>
 *   <li>Rotas públicas: {@code /actuator/health/**}, {@code /actuator/info},
 *       {@code /webhooks/**}, {@code /auth/**}, {@code /error}.</li>
 *   <li>Todo o resto exige JWT válido — sem autenticação ou com JWT
 *       inválido/expirado, a resposta é 401 (sem WWW-Authenticate).</li>
 *   <li>CORS habilitado para {@link SecurityProperties#corsAllowedOrigins}
 *       — cobre o cookie {@code refresh_token} via {@code allowCredentials}.</li>
 * </ul>
 *
 * Esta task (T-021) só faz: registrar o filtro, configurar CORS e fechar
 * as rotas autenticadas. Logout ({@code DELETE /auth/sessions/current})
 * entra em T-022.
 */
@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final SecurityProperties securityProperties;

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(c -> c.configurationSource(corsConfigurationSource()))
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                // Webhook do Evolution: sem auth dedicada (analise-tecnica §10.3).
                .requestMatchers("/webhooks/**").permitAll()
                // OTP request/verify e refresh do JWT são públicos.
                .requestMatchers("/auth/**").permitAll()
                // /error é dispatch interno do Spring (forward em 404/405).
                .requestMatchers("/error").permitAll()
                .anyRequest().authenticated()
            )
            .exceptionHandling(e -> e
                .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                .accessDeniedHandler((req, res, ex) -> res.setStatus(HttpServletResponse.SC_FORBIDDEN))
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(securityProperties.corsAllowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        config.setExposedHeaders(List.of("Set-Cookie"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}

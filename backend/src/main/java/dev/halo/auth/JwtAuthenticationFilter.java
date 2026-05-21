package dev.halo.auth;

import dev.halo.user.User;
import dev.halo.user.UserRepository;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Filtro do Spring Security (T-021, RF-09) que valida o access token JWT
 * presente em {@code Authorization: Bearer <token>} e popula o
 * {@link SecurityContextHolder} com o {@link User} resolvido a partir
 * do {@code sub} (userId).
 *
 * Se o header está ausente, a requisição continua sem autenticação — a
 * decisão final (200 vs 401) fica com o {@code SecurityFilterChain}. Se o
 * header está presente mas inválido (assinatura, expirado, malformado, ou
 * usuário inexistente), apenas registramos em DEBUG e seguimos sem
 * autenticar — o resultado prático é o mesmo 401 da rota protegida, sem
 * vazar a causa.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain chain) throws ServletException, IOException {

        String token = extractBearerToken(request);
        if (token != null) {
            authenticate(token);
        }
        chain.doFilter(request, response);
    }

    private void authenticate(String token) {
        try {
            JwtService.ParsedAccessToken parsed = jwtService.parseAccessToken(token);
            Optional<User> user = userRepository.findById(parsed.userId());
            if (user.isEmpty()) {
                log.debug("JWT válido mas usuário não existe userId={}", parsed.userId());
                return;
            }
            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(user.get(), null, List.of());
            SecurityContextHolder.getContext().setAuthentication(auth);
        } catch (JwtException e) {
            log.debug("JWT rejeitado: {}", e.getMessage());
        }
    }

    private static String extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }
}

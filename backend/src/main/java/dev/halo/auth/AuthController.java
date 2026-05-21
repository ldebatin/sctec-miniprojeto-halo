package dev.halo.auth;

import dev.halo.user.User;
import dev.halo.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Rota de rotação de sessão (T-021, RF-09): {@code POST /auth/refresh}.
 *
 * Lê o cookie {@code refresh_token}, valida (existe, não revogado, não
 * expirado), revoga o anterior, emite um novo refresh + novo access JWT
 * e devolve o access token no body com o novo refresh em cookie.
 *
 * Esta task (T-021) só faz o caminho feliz + 401 para qualquer falha.
 * Logout (revogar a sessão atual sem rotacionar) entra em T-022.
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final RefreshTokenService refreshTokenService;
    private final JwtService jwtService;
    private final UserRepository userRepository;

    @PostMapping("/refresh")
    public ResponseEntity<OtpController.VerifyResponse> refresh(HttpServletRequest request) {
        String plaintext = AuthCookies.readRefreshCookie(request);

        final RefreshTokenService.RotatedRefreshToken rotated;
        try {
            rotated = refreshTokenService.rotate(
                    plaintext,
                    request.getHeader(HttpHeaders.USER_AGENT),
                    AuthCookies.clientIp(request));
        } catch (RefreshTokenException e) {
            log.debug("Refresh rejeitado: {}", e.getMessage());
            return ResponseEntity.status(401).build();
        }

        User user = userRepository.findById(rotated.userId())
                .orElse(null);
        if (user == null) {
            log.warn("Refresh válido mas usuário não existe userId={}", rotated.userId());
            return ResponseEntity.status(401).build();
        }

        JwtService.IssuedAccessToken access =
                jwtService.issueAccessToken(user.getId(), user.getPhone());

        ResponseCookie cookie = AuthCookies.buildRefreshCookie(
                rotated.issued().token(), rotated.issued().ttl());

        OtpController.VerifyResponse body = new OtpController.VerifyResponse(
                access.token(), access.expiresIn().toSeconds());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(body);
    }
}

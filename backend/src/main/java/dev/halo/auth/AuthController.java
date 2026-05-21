package dev.halo.auth;

import dev.halo.user.User;
import dev.halo.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Rotas de sessão do RF-09/RF-11:
 *
 * <ul>
 *   <li>{@code POST /auth/refresh} (T-021) — rotaciona o refresh token
 *       no cookie e emite novo access JWT.</li>
 *   <li>{@code DELETE /auth/sessions/current} (T-022) — logout. Revoga
 *       o refresh token associado ao cookie e devolve cookie com
 *       {@code Max-Age=0} para o navegador descartar.</li>
 * </ul>
 *
 * Logout é idempotente do ponto de vista do cliente: cookie ausente,
 * inválido, expirado ou já revogado todos respondem 204 — o objetivo é
 * deixar o cliente sem credencial, não falhar em estados intermediários.
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

    @DeleteMapping("/sessions/current")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        String plaintext = AuthCookies.readRefreshCookie(request);
        refreshTokenService.revoke(plaintext);

        ResponseCookie cookie = AuthCookies.expiredRefreshCookie();
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .build();
    }
}

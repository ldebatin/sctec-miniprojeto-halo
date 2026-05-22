package dev.halo.auth;

import dev.halo.user.InvalidPhoneException;
import dev.halo.user.User;
import dev.halo.user.UserController;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints de autenticação via OTP (RF-09).
 *
 * <ul>
 *   <li>{@code POST /auth/otp/request} — gera e envia o código via WhatsApp (T-019).</li>
 *   <li>{@code POST /auth/otp/verify} — valida o código e emite access JWT
 *       (15min, body) + refresh token (30d, cookie httpOnly; Secure;
 *       SameSite=Strict) (T-020).</li>
 * </ul>
 *
 * Esta task (T-020) só faz: validar o OTP, emitir tokens e devolver a
 * resposta. O filtro JWT do Spring Security e o endpoint
 * {@code POST /auth/refresh} entram em T-021.
 *
 * Em {@code request}: 429 quando o cooldown de 60s ainda está ativo, 400
 * para telefone inválido, 200 caso contrário (sem revelar se o telefone
 * existe em {@code users}).
 *
 * Em {@code verify}: 401 para qualquer falha (código ausente/errado/
 * expirado, telefone sem usuário), 200 com tokens em caso de sucesso.
 */
@RestController
@RequestMapping("/auth/otp")
@RequiredArgsConstructor
@Slf4j
public class OtpController {

    static final String REFRESH_COOKIE_NAME = AuthCookies.REFRESH_COOKIE_NAME;

    private final OtpService otpService;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    @PostMapping("/request")
    public ResponseEntity<Void> send(@Valid @RequestBody SendOtpRequest request) {
        try {
            otpService.send(request.phone());
        } catch (OtpCooldownException e) {
            log.debug("OTP rejeitado por cooldown phone={}", e.getPhone());
            return ResponseEntity.status(429).build();
        } catch (InvalidPhoneException e) {
            log.debug("OTP rejeitado por telefone inválido: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok().build();
    }

    @PostMapping("/verify")
    public ResponseEntity<VerifyResponse> verify(
            @Valid @RequestBody VerifyOtpRequest request,
            HttpServletRequest servletRequest) {

        final User user;
        try {
            user = otpService.verify(request.phone(), request.code());
        } catch (OtpVerificationException | InvalidPhoneException e) {
            log.debug("Verificação de OTP falhou: {}", e.getMessage());
            return ResponseEntity.status(401).build();
        }

        JwtService.IssuedAccessToken access =
                jwtService.issueAccessToken(user.getId(), user.getPhone());

        String userAgent = servletRequest.getHeader("User-Agent");
        String ip = AuthCookies.clientIp(servletRequest);
        RefreshTokenService.IssuedRefreshToken refresh =
                refreshTokenService.issue(user.getId(), userAgent, ip);

        ResponseCookie cookie = AuthCookies.buildRefreshCookie(refresh.token(), refresh.ttl());

        VerifyResponse body = new VerifyResponse(
                access.token(),
                access.expiresIn().toSeconds(),
                UserController.MeResponse.from(user));

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(body);
    }

    /** Payload de entrada para {@code POST /auth/otp/request}. */
    record SendOtpRequest(@NotBlank String phone) {}

    /** Payload de entrada para {@code POST /auth/otp/verify}. */
    record VerifyOtpRequest(
            @NotBlank String phone,
            @NotBlank @Pattern(regexp = "\\d{6}", message = "código deve ter 6 dígitos") String code
    ) {}

    /**
     * Resposta do {@code POST /auth/otp/verify} e do {@code POST /auth/refresh}.
     * O refresh token vai em cookie httpOnly e não aparece no body.
     *
     * @param accessToken JWT HMAC-SHA256 com TTL de {@code expiresIn} segundos.
     * @param expiresIn   tempo de vida do access token em segundos.
     * @param user        snapshot do {@link User} autenticado — economiza um
     *                    {@code GET /me} subsequente do frontend.
     */
    public record VerifyResponse(
            String accessToken,
            long expiresIn,
            UserController.MeResponse user
    ) {}
}

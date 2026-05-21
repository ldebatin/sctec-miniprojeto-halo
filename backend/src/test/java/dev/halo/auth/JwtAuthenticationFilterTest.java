package dev.halo.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.halo.user.User;
import dev.halo.user.UserRepository;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Unit do {@link JwtAuthenticationFilter}: presença/ausência de header,
 * validade do JWT, e existência do usuário.
 */
class JwtAuthenticationFilterTest {

    private JwtService jwtService;
    private UserRepository userRepository;
    private FilterChain chain;
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        jwtService = mock(JwtService.class);
        userRepository = mock(UserRepository.class);
        chain = mock(FilterChain.class);
        filter = new JwtAuthenticationFilter(jwtService, userRepository);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void sem_authorization_passa_adiante_sem_autenticar() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(jwtService, never()).parseAccessToken(any());
        verify(chain).doFilter(req, res);
    }

    @Test
    void com_jwt_valido_popula_security_context_com_user() throws Exception {
        UUID userId = UUID.randomUUID();
        User user = userCom(userId);
        when(jwtService.parseAccessToken("valid-jwt"))
                .thenReturn(new JwtService.ParsedAccessToken(userId, "+5547999999999"));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer valid-jwt");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
                .isSameAs(user);
        verify(chain).doFilter(req, res);
    }

    @Test
    void com_jwt_invalido_nao_autentica_mas_segue_a_chain() throws Exception {
        when(jwtService.parseAccessToken("bad-jwt"))
                .thenThrow(new JwtException("assinatura inválida"));

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer bad-jwt");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(req, res);
    }

    @Test
    void com_jwt_valido_mas_user_inexistente_nao_autentica() throws Exception {
        UUID userId = UUID.randomUUID();
        when(jwtService.parseAccessToken("ghost-jwt"))
                .thenReturn(new JwtService.ParsedAccessToken(userId, "+5547999999999"));
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer ghost-jwt");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(req, res);
    }

    @Test
    void header_com_prefixo_errado_e_ignorado() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Basic dXNlcjpwYXNz");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(jwtService, never()).parseAccessToken(any());
        verify(chain).doFilter(req, res);
    }

    private User userCom(UUID id) {
        User user = new User();
        user.setId(id);
        user.setPhone("+5547999999999");
        user.setName("Carla");
        Instant now = Instant.now();
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        return user;
    }
}

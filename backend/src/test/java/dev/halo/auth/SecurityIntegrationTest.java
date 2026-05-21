package dev.halo.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;

import dev.halo.user.User;
import dev.halo.user.UserRepository;
import dev.halo.whatsapp.EvolutionClient;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integração da cadeia de segurança (T-021, RF-09) com servidor real.
 *
 * Cobre:
 * <ul>
 *   <li>Rota protegida ({@code /categories}) sem JWT → 401.</li>
 *   <li>Mesma rota com JWT válido → 200.</li>
 *   <li>Mesma rota com JWT malformado → 401.</li>
 *   <li>{@code /auth/otp/request} segue público.</li>
 *   <li>Preflight CORS para origem permitida devolve os headers esperados.</li>
 *   <li>Preflight CORS para origem **não** permitida é rejeitado.</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class SecurityIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @LocalServerPort
    private int port;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private EvolutionClient evolutionClient;

    @BeforeEach
    void setUp() {
        // OtpService.send chama o Evolution em endpoints públicos — em CI
        // não há instância Evolution rodando, então mockamos.
        doNothing().when(evolutionClient).sendText(any(), any());
    }

    @Test
    void rota_protegida_sem_jwt_retorna_401() {
        ResponseEntity<String> response = RestClient.create()
                .get()
                .uri("http://localhost:{port}/categories", port)
                .retrieve()
                .onStatus(s -> s.is4xxClientError() || s.is5xxServerError(), (req, res) -> {})
                .toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void rota_protegida_com_jwt_invalido_retorna_401() {
        ResponseEntity<String> response = RestClient.create()
                .get()
                .uri("http://localhost:{port}/categories", port)
                .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-real-jwt")
                .retrieve()
                .onStatus(s -> s.is4xxClientError() || s.is5xxServerError(), (req, res) -> {})
                .toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void rota_protegida_com_jwt_valido_retorna_200() {
        User user = criarUser("+5547100000001");
        String token = jwtService.issueAccessToken(user.getId(), user.getPhone()).token();

        ResponseEntity<String> response = RestClient.create()
                .get()
                .uri("http://localhost:{port}/categories", port)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .onStatus(s -> s.is4xxClientError() || s.is5xxServerError(), (req, res) -> {})
                .toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void auth_otp_request_segue_publico_sem_jwt() {
        ResponseEntity<String> response = RestClient.create()
                .post()
                .uri("http://localhost:{port}/auth/otp/request", port)
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .body("{\"phone\":\"+5547100000099\"}")
                .retrieve()
                .onStatus(s -> s.is4xxClientError() || s.is5xxServerError(), (req, res) -> {})
                .toEntity(String.class);

        // OK ou 429 (cooldown) — qualquer um significa que a rota é pública.
        assertThat(response.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void cors_preflight_para_origem_permitida_devolve_headers() throws Exception {
        java.net.http.HttpResponse<Void> response = enviarPreflight(
                "http://localhost:5173", "POST", "Authorization,Content-Type");

        assertThat(response.statusCode()).isBetween(200, 299);
        assertThat(response.headers().firstValue("Access-Control-Allow-Origin"))
                .hasValue("http://localhost:5173");
        assertThat(response.headers().firstValue("Access-Control-Allow-Credentials"))
                .hasValue("true");
        assertThat(response.headers().firstValue("Access-Control-Allow-Methods"))
                .hasValueSatisfying(v -> assertThat(v).contains("POST"));
    }

    @Test
    void cors_preflight_para_origem_nao_permitida_e_rejeitado() throws Exception {
        java.net.http.HttpResponse<Void> response = enviarPreflight(
                "https://invasor.example", "POST", "Authorization");

        // Spring devolve 403 ou 200 sem headers; o critério é não ter
        // Access-Control-Allow-Origin para a origem rejeitada.
        assertThat(response.headers().firstValue("Access-Control-Allow-Origin"))
                .isEmpty();
    }

    private java.net.http.HttpResponse<Void> enviarPreflight(
            String origin, String requestMethod, String requestHeaders) throws Exception {
        java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create("http://localhost:" + port + "/auth/refresh"))
                .method("OPTIONS", java.net.http.HttpRequest.BodyPublishers.noBody())
                .header("Origin", origin)
                .header("Access-Control-Request-Method", requestMethod)
                .header("Access-Control-Request-Headers", requestHeaders)
                .build();
        return client.send(request, java.net.http.HttpResponse.BodyHandlers.discarding());
    }

    private User criarUser(String phone) {
        return userRepository.findByPhone(phone).orElseGet(() -> {
            User user = new User();
            user.setName("Carla");
            user.setPhone(phone);
            Instant now = Instant.now();
            user.setCreatedAt(now);
            user.setUpdatedAt(now);
            return userRepository.save(user);
        });
    }
}

package dev.halo.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integração T-010: contrato do {@link UserService#findOrNull(String)}.
 */
@SpringBootTest
@Testcontainers
@Transactional
class UserServiceIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    private User insertUser(String phoneE164, String name) {
        User user = new User();
        user.setName(name);
        user.setPhone(phoneE164);
        Instant now = Instant.now();
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        return userRepository.save(user);
    }

    @Test
    void findOrNull_devolve_user_quando_existe() {
        User joao = insertUser("+5547999999999", "João");

        User found = userService.findOrNull("5547999999999@s.whatsapp.net");

        assertThat(found).isNotNull();
        assertThat(found.getId()).isEqualTo(joao.getId());
        assertThat(found.getName()).isEqualTo("João");
    }

    @Test
    void findOrNull_devolve_null_quando_nao_ha_cadastro() {
        User found = userService.findOrNull("+5547000000000");

        assertThat(found).isNull();
    }

    @Test
    void findOrNull_propaga_exception_para_telefone_invalido() {
        assertThatThrownBy(() -> userService.findOrNull("abc"))
                .isInstanceOf(InvalidPhoneException.class);
    }
}

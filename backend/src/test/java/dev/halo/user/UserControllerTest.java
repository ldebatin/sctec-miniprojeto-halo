package dev.halo.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Unit do {@link UserController} via MockMvc standalone com o
 * {@link AuthenticationPrincipalArgumentResolver} registrado para resolver
 * {@code @AuthenticationPrincipal User user}.
 */
class UserControllerTest {

    private UserRepository userRepository;
    private MockMvc mvc;
    private User currentUser;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        currentUser = userCom("Carla");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(currentUser, null, java.util.List.of()));

        mvc = MockMvcBuilders
                .standaloneSetup(new UserController(userRepository))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void get_me_retorna_perfil_do_usuario_autenticado() throws Exception {
        mvc.perform(get("/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(currentUser.getId().toString()))
                .andExpect(jsonPath("$.name").value("Carla"))
                .andExpect(jsonPath("$.phone").value("+5547999999999"));
    }

    @Test
    void patch_me_atualiza_name_e_retorna_perfil() throws Exception {
        mvc.perform(patch("/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Carla S.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Carla S."));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("Carla S.");
    }

    @Test
    void patch_me_faz_trim_no_name() throws Exception {
        mvc.perform(patch("/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  Carla S.  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Carla S."));
    }

    @Test
    void patch_me_atualiza_updated_at() throws Exception {
        Instant before = currentUser.getUpdatedAt();

        mvc.perform(patch("/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Nova\"}"));

        assertThat(currentUser.getUpdatedAt()).isAfter(before);
    }

    @Test
    void patch_me_rejeita_name_com_menos_de_2_chars() throws Exception {
        mvc.perform(patch("/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"A\"}"))
                .andExpect(status().isBadRequest());

        verify(userRepository, never()).save(any());
    }

    @Test
    void patch_me_rejeita_name_em_branco() throws Exception {
        mvc.perform(patch("/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"   \"}"))
                .andExpect(status().isBadRequest());

        verify(userRepository, never()).save(any());
    }

    @Test
    void patch_me_rejeita_name_ausente() throws Exception {
        mvc.perform(patch("/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verify(userRepository, never()).save(any());
    }

    @Test
    void patch_me_ignora_phone_no_body() throws Exception {
        mvc.perform(patch("/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Nova\",\"phone\":\"+5511111111111\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value("+5547999999999"));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getPhone()).isEqualTo("+5547999999999");
    }

    private User userCom(String name) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setName(name);
        user.setPhone("+5547999999999");
        Instant now = Instant.now().minusSeconds(60);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        return user;
    }
}

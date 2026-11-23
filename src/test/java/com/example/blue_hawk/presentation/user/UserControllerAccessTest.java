package com.example.blue_hawk.presentation.user;

import com.example.blue_hawk.application.dto.user.UserOutput;
import com.example.blue_hawk.application.usecase.user.AuthenticateUserUseCase;
import com.example.blue_hawk.application.usecase.user.ChangeUserPasswordUseCase;
import com.example.blue_hawk.application.usecase.user.CreateUserUseCase;
import com.example.blue_hawk.application.usecase.user.DeleteUserUseCase;
import com.example.blue_hawk.application.usecase.user.GetUserUseCase;
import com.example.blue_hawk.application.usecase.user.ListUserUseCase;
import com.example.blue_hawk.application.usecase.user.UpdateUserUseCase;
import com.example.blue_hawk.domain.repository.IUserRepository;
import com.example.blue_hawk.infrastructure.security.JwtTokenService;
import com.example.blue_hawk.infrastructure.security.SecurityConfig;
import com.example.blue_hawk.infrastructure.security.TokenBlacklistService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Permissões das rotas /users: o próprio usuário ou ADMIN altera/exclui; só o próprio troca a senha. */
@WebMvcTest(
        controllers = UserController.class,
        properties = {
                "security.jwt.secret=test-secret-with-at-least-32-characters!!",
                "security.jwt.expiration-seconds=3600",
                "app.cors.allowed-origins=http://localhost:5173"
        })
@Import({SecurityConfig.class, JwtTokenService.class, TokenBlacklistService.class})
class UserControllerAccessTest {

    private static final UUID ME = UUID.randomUUID();
    private static final UUID SOMEONE = UUID.randomUUID();
    private static final String UPDATE_BODY = "{\"name\":\"Novo Nome\"}";
    private static final String PASSWORD_BODY = "{\"currentPassword\":\"senha-antiga\",\"newPassword\":\"senha-nova-123\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CreateUserUseCase createUserUseCase;
    @MockitoBean
    private AuthenticateUserUseCase authenticateUserUseCase;
    @MockitoBean
    private UpdateUserUseCase updateUserUseCase;
    @MockitoBean
    private ChangeUserPasswordUseCase changeUserPasswordUseCase;
    @MockitoBean
    private GetUserUseCase getUserUseCase;
    @MockitoBean
    private ListUserUseCase listUserUseCase;
    @MockitoBean
    private DeleteUserUseCase deleteUserUseCase;
    @MockitoBean
    private IUserRepository userRepository;

    private static RequestPostProcessor loggedAs(UUID id, String role) {
        return jwt().jwt(token -> token.subject(id.toString())).authorities(new SimpleGrantedAuthority("SCOPE_" + role));
    }

    @Test
    void userUpdatesOwnAccount() throws Exception {
        when(updateUserUseCase.handle(any())).thenReturn(new UserOutput(ME.toString(), "Novo Nome", "eu@x.com", "USER"));

        mockMvc.perform(put("/users/" + ME).with(loggedAs(ME, "USER"))
                        .contentType(MediaType.APPLICATION_JSON).content(UPDATE_BODY))
                .andExpect(status().isOk());
    }

    @Test
    void userCannotUpdateOrDeleteSomeoneElse() throws Exception {
        mockMvc.perform(put("/users/" + SOMEONE).with(loggedAs(ME, "USER"))
                        .contentType(MediaType.APPLICATION_JSON).content(UPDATE_BODY))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/users/" + SOMEONE).with(loggedAs(ME, "USER")))
                .andExpect(status().isForbidden());

        verify(updateUserUseCase, never()).handle(any());
        verify(deleteUserUseCase, never()).handle(any());
    }

    @Test
    void adminUpdatesAndDeletesAnyone() throws Exception {
        when(updateUserUseCase.handle(any())).thenReturn(new UserOutput(SOMEONE.toString(), "X", "x@x.com", "USER"));

        mockMvc.perform(patch("/users/" + SOMEONE).with(loggedAs(ME, "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(UPDATE_BODY))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/users/" + SOMEONE).with(loggedAs(ME, "ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void onlyTheUserChangesTheirPasswordNotEvenAdmin() throws Exception {
        mockMvc.perform(patch("/users/" + SOMEONE + "/password").with(loggedAs(ME, "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(PASSWORD_BODY))
                .andExpect(status().isForbidden());
        verify(changeUserPasswordUseCase, never()).handle(any());

        mockMvc.perform(patch("/users/" + ME + "/password").with(loggedAs(ME, "USER"))
                        .contentType(MediaType.APPLICATION_JSON).content(PASSWORD_BODY))
                .andExpect(status().isOk());
    }

    @Test
    void visitorsNeedToLogIn() throws Exception {
        mockMvc.perform(delete("/users/" + ME)).andExpect(status().isUnauthorized());
    }
}

package com.example.blue_hawk.application.usecase.user;

import com.example.blue_hawk.application.dto.auth.CurrentUser;
import com.example.blue_hawk.application.dto.user.ChangeUserPasswordCommand;
import com.example.blue_hawk.application.dto.user.DeleteUserCommand;
import com.example.blue_hawk.application.dto.user.UpdateUserCommand;
import com.example.blue_hawk.application.port.RealtimeConnections;
import com.example.blue_hawk.domain.entity.user.User;
import com.example.blue_hawk.domain.entity.user.UserRole;
import com.example.blue_hawk.domain.repository.IUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Conta de usuário: o próprio usuário ou ADMIN altera/exclui; só o próprio troca a senha. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserAccountAccessTest {

    private static final UUID ACCOUNT = UUID.randomUUID();
    private static final CurrentUser OWNER = new CurrentUser(ACCOUNT, false);
    private static final CurrentUser STRANGER = new CurrentUser(UUID.randomUUID(), false);
    private static final CurrentUser ADMIN = new CurrentUser(UUID.randomUUID(), true);

    @Mock
    private IUserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private RealtimeConnections realtimeConnections;

    @BeforeEach
    void setUp() {
        User user = new User(ACCOUNT, "Alice", "alice@example.com", "hash", UserRole.USER);
        when(userRepository.findById(ACCOUNT)).thenReturn(Optional.of(user));
        when(userRepository.existsById(ACCOUNT)).thenReturn(true);
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(passwordEncoder.matches(any(), any())).thenReturn(true);
        when(passwordEncoder.encode(any())).thenReturn("new-hash");
    }

    private static void assertStatus(HttpStatus expected, Executable action) {
        assertEquals(expected, assertThrows(ResponseStatusException.class, action).getStatusCode());
    }

    private void update(CurrentUser user) {
        new UpdateUserUseCaseImpl(userRepository).handle(new UpdateUserCommand(ACCOUNT.toString(), "Novo", null, user));
    }

    private void delete(CurrentUser user) {
        new DeleteUserUseCaseImpl(userRepository, realtimeConnections)
                .handle(new DeleteUserCommand(ACCOUNT.toString(), user));
    }

    private void changePassword(CurrentUser user) {
        new ChangeUserPasswordUseCaseImpl(userRepository, passwordEncoder)
                .handle(new ChangeUserPasswordCommand(ACCOUNT.toString(), "senha-antiga", "senha-nova-123", user));
    }

    @Test
    void strangerCannotUpdateOrDeleteSomeoneElsesAccount() {
        assertStatus(HttpStatus.FORBIDDEN, () -> update(STRANGER));
        assertStatus(HttpStatus.FORBIDDEN, () -> delete(STRANGER));
        assertStatus(HttpStatus.UNAUTHORIZED, () -> update(CurrentUser.anonymous()));
        assertStatus(HttpStatus.UNAUTHORIZED, () -> delete(null));

        verify(userRepository, never()).save(any());
        verify(userRepository, never()).deleteById(any());
    }

    @Test
    void ownerAndAdminUpdateAndDeleteTheAccount() {
        assertDoesNotThrow(() -> update(OWNER));
        assertDoesNotThrow(() -> update(ADMIN));
        assertDoesNotThrow(() -> delete(ADMIN));
        verify(userRepository).deleteById(ACCOUNT);
    }

    @Test
    void onlyTheUserChangesTheirPasswordNotEvenAdmin() {
        assertStatus(HttpStatus.FORBIDDEN, () -> changePassword(ADMIN));
        assertStatus(HttpStatus.FORBIDDEN, () -> changePassword(STRANGER));
        assertStatus(HttpStatus.UNAUTHORIZED, () -> changePassword(CurrentUser.anonymous()));
        verify(userRepository, never()).save(any());

        assertDoesNotThrow(() -> changePassword(OWNER));
    }
}

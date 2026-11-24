package com.example.blue_hawk.presentation.user;

import org.springframework.security.access.prepost.PreAuthorize;
import com.example.blue_hawk.presentation.auth.Roles;

import com.example.blue_hawk.application.dto.auth.CurrentUser;
import com.example.blue_hawk.application.dto.user.AuthenticateUserCommand;
import com.example.blue_hawk.application.dto.user.AuthenticateUserOutput;
import com.example.blue_hawk.application.dto.user.ChangeUserPasswordCommand;
import com.example.blue_hawk.application.dto.user.CreateUserCommand;
import com.example.blue_hawk.application.dto.user.DeleteUserCommand;
import com.example.blue_hawk.application.dto.user.GetUserQuery;
import com.example.blue_hawk.application.dto.user.ListUserQuery;
import com.example.blue_hawk.application.dto.user.UpdateUserCommand;
import com.example.blue_hawk.application.dto.user.UserOutput;
import com.example.blue_hawk.application.usecase.user.AuthenticateUserUseCase;
import com.example.blue_hawk.application.usecase.user.ChangeUserPasswordUseCase;
import com.example.blue_hawk.application.usecase.user.CreateUserUseCase;
import com.example.blue_hawk.application.usecase.user.DeleteUserUseCase;
import com.example.blue_hawk.application.usecase.user.GetUserUseCase;
import com.example.blue_hawk.application.usecase.user.ListUserUseCase;
import com.example.blue_hawk.application.usecase.user.UpdateUserUseCase;
import com.example.blue_hawk.presentation.dto.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Contas de usuário. Alterar e excluir: só o próprio usuário ou um ADMIN. Trocar a senha: só o próprio usuário.
 * As regras usam o {@link CurrentUser} da requisição (não vão nos commands, que também são o corpo JSON e
 * poderiam ser preenchidos pelo cliente).
 */
@RestController
@RequestMapping("/users")
public class UserController {

    private final CreateUserUseCase createUserUseCase;
    private final AuthenticateUserUseCase authenticateUserUseCase;
    private final UpdateUserUseCase updateUserUseCase;
    private final ChangeUserPasswordUseCase changeUserPasswordUseCase;
    private final GetUserUseCase getUserUseCase;
    private final ListUserUseCase listUserUseCase;
    private final DeleteUserUseCase deleteUserUseCase;

    public UserController(CreateUserUseCase createUserUseCase,
                          AuthenticateUserUseCase authenticateUserUseCase,
                          UpdateUserUseCase updateUserUseCase,
                          ChangeUserPasswordUseCase changeUserPasswordUseCase,
                          GetUserUseCase getUserUseCase,
                          ListUserUseCase listUserUseCase,
                          DeleteUserUseCase deleteUserUseCase) {
        this.createUserUseCase = createUserUseCase;
        this.authenticateUserUseCase = authenticateUserUseCase;
        this.updateUserUseCase = updateUserUseCase;
        this.changeUserPasswordUseCase = changeUserPasswordUseCase;
        this.getUserUseCase = getUserUseCase;
        this.listUserUseCase = listUserUseCase;
        this.deleteUserUseCase = deleteUserUseCase;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserOutput create(@Valid @RequestBody CreateUserCommand command) {
        return createUserUseCase.handle(command);
    }

    @PostMapping("/authenticate")
    public AuthenticateUserOutput authenticate(@Valid @RequestBody AuthenticateUserCommand command) {
        return authenticateUserUseCase.handle(command);
    }

    // PUT e PATCH usam o mesmo caso de uso: só os campos enviados são alterados.
    // Quem pode alterar, excluir ou ver cada conta é decidido nos casos de uso, a partir do currentUser.
    @PutMapping("/{uuid}")
    public UserOutput update(@PathVariable UUID uuid, @Valid @RequestBody UpdateUserCommand body,
                             CurrentUser currentUser) {
        return updateUserUseCase.handle(new UpdateUserCommand(uuid.toString(), body.name(), body.email(), currentUser));
    }

    @PatchMapping("/{uuid}")
    public UserOutput patch(@PathVariable UUID uuid, @Valid @RequestBody UpdateUserCommand body,
                            CurrentUser currentUser) {
        return update(uuid, body, currentUser);
    }

    @PatchMapping("/{uuid}/password")
    public ApiResponse<Void> changePassword(@PathVariable UUID uuid, @Valid @RequestBody ChangeUserPasswordCommand body,
                                            CurrentUser currentUser) {
        changeUserPasswordUseCase.handle(new ChangeUserPasswordCommand(uuid.toString(), body.currentPassword(),
                body.newPassword(), currentUser));
        return ApiResponse.empty();
    }

    @GetMapping("/{uuid}")
    public UserOutput get(@PathVariable UUID uuid, CurrentUser currentUser) {
        return getUserUseCase.handle(new GetUserQuery(uuid.toString(), currentUser));
    }

    /** Lista (com busca por e-mail) só para ADMIN: senão qualquer conta enumeraria os e-mails da base */
    @GetMapping
    @PreAuthorize(Roles.ADMIN_ONLY)
    public List<UserOutput> list(@RequestParam(required = false) String name,
                                 @RequestParam(required = false) String email,
                                 @RequestParam(required = false) Integer page,
                                 @RequestParam(required = false) Integer size) {
        return listUserUseCase.handle(new ListUserQuery(name, email, page, size));
    }

    @DeleteMapping("/{uuid}")
    public ApiResponse<Void> delete(@PathVariable UUID uuid, CurrentUser currentUser) {
        deleteUserUseCase.handle(new DeleteUserCommand(uuid.toString(), currentUser));
        return ApiResponse.empty();
    }
}

package com.example.blue_hawk.application.usecase.user;

import com.example.blue_hawk.application.usecase.auth.Access;

import com.example.blue_hawk.application.dto.user.DeleteUserCommand;
import com.example.blue_hawk.application.port.RealtimeConnections;
import com.example.blue_hawk.domain.repository.IUserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
public class DeleteUserUseCaseImpl implements DeleteUserUseCase {

    private final IUserRepository userRepository;
    private final RealtimeConnections realtimeConnections;

    public DeleteUserUseCaseImpl(IUserRepository userRepository, RealtimeConnections realtimeConnections) {
        this.userRepository = userRepository;
        this.realtimeConnections = realtimeConnections;
    }

    @Override
    public void handle(DeleteUserCommand command) {
        UUID userId = UUID.fromString(command.id());
        Access.requireSelfOrAdmin(userId, command.currentUser(), "You can only change your own account");
        if (!userRepository.existsById(userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
        // Exclusão física: remove o registro da tabela.
        userRepository.deleteById(userId);
        // A conta não existe mais: fecha as conexões de tempo real que ela ainda tinha abertas
        realtimeConnections.disconnectUser(userId);
    }
}

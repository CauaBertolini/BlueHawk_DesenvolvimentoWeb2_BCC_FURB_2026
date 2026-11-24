package com.example.blue_hawk.application.usecase.userboardgame;

import com.example.blue_hawk.application.usecase.auth.Access;
import com.example.blue_hawk.application.dto.auth.CurrentUser;

import com.example.blue_hawk.application.dto.userboardgame.DeleteUserBoardgameCommand;
import com.example.blue_hawk.application.dto.userboardgame.DeleteUserBoardgameOutput;
import com.example.blue_hawk.domain.repository.IUserBoardGameRepository;

import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class DeleteUserBoardgameUseCaseImpl implements DeleteUserBoardgameUseCase {

    private final IUserBoardGameRepository userBoardGameRepository;

    public DeleteUserBoardgameUseCaseImpl(IUserBoardGameRepository userBoardGameRepository) {
        this.userBoardGameRepository = userBoardGameRepository;
    }

    @Override
    public DeleteUserBoardgameOutput handle(DeleteUserBoardgameCommand command) {
        UUID id = UUID.fromString(command.id());
        CurrentUser user = Access.requireLogin(command.currentUser());
        // Cópia inexistente: nada a apagar (a exclusão continua idempotente)
        userBoardGameRepository.findById(id).ifPresent(copy -> Access.requireShelfOwner(copy, user));
        userBoardGameRepository.deleteById(id);

        return new DeleteUserBoardgameOutput(command.id());
    }

}

package com.example.blue_hawk.application.usecase.userboardgame;

import com.example.blue_hawk.application.usecase.auth.Access;

import com.example.blue_hawk.application.dto.userboardgame.UpdateUserBoardgameCommand;
import com.example.blue_hawk.application.dto.userboardgame.UpdateUserBoardgameOutput;
import com.example.blue_hawk.domain.entity.userboardgame.UserBoardgame;
import com.example.blue_hawk.domain.repository.IUserBoardGameRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class UpdateUserBoardgameUseCaseImpl implements UpdateUserBoardgameUseCase {

    private final IUserBoardGameRepository userBoardGameRepository;

    public UpdateUserBoardgameUseCaseImpl(IUserBoardGameRepository userBoardGameRepository) {
        this.userBoardGameRepository = userBoardGameRepository;
    }

    @Override
    public UpdateUserBoardgameOutput handle(UpdateUserBoardgameCommand command) {
        UserBoardgame userBoardgame = userBoardGameRepository
                .findById(UUID.fromString(command.id()))
                .orElseThrow(() -> new RuntimeException("UserBoardgame not found"));
        // Cada um mexe só na própria estante (ADMIN pode mexer em qualquer uma): vale para a cópia e para o novo dono
        Access.requireShelfOwner(userBoardgame, command.currentUser());
        UUID newOwnerId = UUID.fromString(command.userId());
        Access.requireSelfOrAdmin(newOwnerId, command.currentUser(), "You can only keep games on your own shelf");

        userBoardgame.setUserId(newOwnerId);
        userBoardgame.setBoardgameId(UUID.fromString(command.boardgameId()));

        UserBoardgame updated = userBoardGameRepository.save(userBoardgame);

        return new UpdateUserBoardgameOutput(
                updated.getId().toString(),
                updated.getUserId().toString(),
                updated.getBoardgameId().toString());
    }
}

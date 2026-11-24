package com.example.blue_hawk.application.usecase.userboardgame;

import com.example.blue_hawk.application.dto.auth.CurrentUser;
import com.example.blue_hawk.application.dto.userboardgame.CreateUserBoardgameCommand;
import com.example.blue_hawk.application.dto.userboardgame.CreateUserBoardgameOutput;
import com.example.blue_hawk.domain.entity.userboardgame.UserBoardgame;
import com.example.blue_hawk.domain.repository.IBoardgameRepository;
import com.example.blue_hawk.domain.repository.IUserBoardGameRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
public class CreateUserBoardgameUseCaseImpl implements CreateUserBoardgameUseCase {
    private final IUserBoardGameRepository userBoardGameRepository;
    private final IBoardgameRepository boardgameRepository;

    public CreateUserBoardgameUseCaseImpl(IUserBoardGameRepository userBoardGameRepository,
                                          IBoardgameRepository boardgameRepository) {
        this.userBoardGameRepository = userBoardGameRepository;
        this.boardgameRepository = boardgameRepository;
    }

    @Override
    public CreateUserBoardgameOutput handle(CreateUserBoardgameCommand command) {
        CurrentUser currentUser = command.currentUser() != null ? command.currentUser() : CurrentUser.anonymous();
        if (command.userId() == null || command.boardgameId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "userId and boardgameId are required");
        }

        UUID userId = UUID.fromString(command.userId());
        // Cada um mexe só na própria estante (ADMIN pode mexer em qualquer uma)
        if (!currentUser.admin() && !userId.equals(currentUser.userId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only add games to your own shelf");
        }

        // Só entra na estante jogo aprovado, ou o jogo pendente/rejeitado que o próprio dono cadastrou
        boardgameRepository.findById(UUID.fromString(command.boardgameId()))
                .filter(bg -> bg.isVisibleTo(userId, currentUser.admin()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Boardgame not found"));

        UserBoardgame userBoardgame = new UserBoardgame(command.userId(), command.boardgameId());
        UserBoardgame savedUserBoardgame = userBoardGameRepository.save(userBoardgame);

        return new CreateUserBoardgameOutput(savedUserBoardgame.getId().toString(),
                savedUserBoardgame.getUserId().toString(), savedUserBoardgame.getBoardgameId().toString());
    }
}

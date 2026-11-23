package com.example.blue_hawk.application.usecase.boardgame;

import com.example.blue_hawk.application.dto.auth.CurrentUser;
import com.example.blue_hawk.application.dto.boardgame.UpdateBoardgameCommand;
import com.example.blue_hawk.application.dto.boardgame.UpdateBoardgameOutput;
import com.example.blue_hawk.domain.entity.boardgame.Boardgame;
import com.example.blue_hawk.domain.repository.IBoardgameRepository;
import com.example.blue_hawk.domain.repository.ICategoryRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * ADMIN edita qualquer jogo. O autor só edita o próprio jogo enquanto ele não foi aprovado;
 * corrigir um jogo rejeitado o devolve para a fila (PENDING).
 */
@Service
public class UpdateBoardgameUseCaseImpl implements UpdateBoardgameUseCase {

    private final IBoardgameRepository boardgameRepository;
    private final ICategoryRepository categoryRepository;

    public UpdateBoardgameUseCaseImpl(IBoardgameRepository boardgameRepository,
                                      ICategoryRepository categoryRepository) {
        this.boardgameRepository = boardgameRepository;
        this.categoryRepository = categoryRepository;
    }

    @Override
    public UpdateBoardgameOutput handle(UpdateBoardgameCommand command) {
        CurrentUser currentUser = command.currentUser() != null ? command.currentUser() : CurrentUser.anonymous();
        Boardgame boardgame = boardgameRepository
                .findById(UUID.fromString(command.id()))
                .filter(bg -> bg.isVisibleTo(currentUser.userId(), currentUser.admin()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Boardgame not found"));

        if (!boardgame.canBeEditedBy(currentUser.userId(), currentUser.admin())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only admins can edit catalog games");
        }

        BoardgameValidation.validate(command.name(), command.description(), command.releaseYear(),
                command.playerNumber(), command.playTime());
        boardgame.setName(command.name().trim());
        boardgame.setDescription(command.description().trim());
        boardgame.setReleaseYear(command.releaseYear());
        boardgame.setPlayerNumber(command.playerNumber());
        boardgame.setPlayTime(command.playTime());
        boardgame.setCategoryId(BoardgameValidation.requireCategory(categoryRepository, command.categoryId()));
        boardgame.setCoverPreset(BoardgameValidation.normalizeCoverPreset(command.coverPreset()));
        if (!currentUser.admin()) {
            boardgame.resubmit();
        }

        return UpdateBoardgameOutput.from(boardgameRepository.save(boardgame));
    }
}

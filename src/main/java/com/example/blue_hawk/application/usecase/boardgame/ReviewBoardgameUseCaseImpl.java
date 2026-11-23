package com.example.blue_hawk.application.usecase.boardgame;

import com.example.blue_hawk.application.dto.boardgame.GetBoardgameOutput;
import com.example.blue_hawk.application.dto.boardgame.ReviewBoardgameCommand;
import com.example.blue_hawk.domain.entity.boardgame.Boardgame;
import com.example.blue_hawk.domain.repository.IBoardgameRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/** Aprovação/rejeição de jogos enviados. A permissão de ADMIN é checada no controller. */
@Service
public class ReviewBoardgameUseCaseImpl implements ReviewBoardgameUseCase {

    private final IBoardgameRepository boardgameRepository;

    public ReviewBoardgameUseCaseImpl(IBoardgameRepository boardgameRepository) {
        this.boardgameRepository = boardgameRepository;
    }

    @Override
    public GetBoardgameOutput handle(ReviewBoardgameCommand command) {
        Boardgame boardgame = boardgameRepository
                .findById(UUID.fromString(command.id()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Boardgame not found"));

        if (command.approve()) {
            boardgame.approve();
        } else {
            String note = command.note() != null && !command.note().isBlank() ? command.note().trim() : null;
            if (note != null && note.length() > 500) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "reason must have at most 500 characters");
            }
            boardgame.reject(note);
        }

        return GetBoardgameOutput.from(boardgameRepository.save(boardgame));
    }
}

package com.example.blue_hawk.application.usecase.boardgame;

import com.example.blue_hawk.application.dto.boardgame.GetBoardgameOutput;
import com.example.blue_hawk.application.dto.boardgame.ReviewBoardgameCommand;
import com.example.blue_hawk.application.usecase.notification.NotificationService;
import com.example.blue_hawk.domain.entity.boardgame.Boardgame;
import com.example.blue_hawk.domain.entity.notification.Notification;
import com.example.blue_hawk.domain.repository.IBoardgameRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * Aprovação/rejeição de jogos enviados. A permissão de ADMIN é checada no controller.
 * Quem enviou o jogo é notificado (a menos que seja o próprio revisor).
 */
@Service
public class ReviewBoardgameUseCaseImpl implements ReviewBoardgameUseCase {

    private final IBoardgameRepository boardgameRepository;
    private final NotificationService notificationService;

    public ReviewBoardgameUseCaseImpl(IBoardgameRepository boardgameRepository,
                                      NotificationService notificationService) {
        this.boardgameRepository = boardgameRepository;
        this.notificationService = notificationService;
    }

    @Override
    @Transactional
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

        Boardgame saved = boardgameRepository.save(boardgame);
        UUID reviewerId = command.currentUser() != null ? command.currentUser().userId() : null;
        notificationService.notifyBoardgame(saved.getSubmittedBy(),
                command.approve() ? Notification.Type.BOARDGAME_APPROVED : Notification.Type.BOARDGAME_REJECTED,
                reviewerId, saved.getId());
        return GetBoardgameOutput.from(saved);
    }
}

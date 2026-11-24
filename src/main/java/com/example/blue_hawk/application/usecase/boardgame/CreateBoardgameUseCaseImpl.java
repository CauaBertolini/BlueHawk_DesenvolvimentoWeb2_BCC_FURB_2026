package com.example.blue_hawk.application.usecase.boardgame;

import com.example.blue_hawk.application.dto.auth.CurrentUser;
import com.example.blue_hawk.application.dto.boardgame.CreateBoardgameCommand;
import com.example.blue_hawk.application.dto.boardgame.CreateBoardgameOutput;
import com.example.blue_hawk.domain.entity.boardgame.Boardgame;
import com.example.blue_hawk.domain.entity.userboardgame.UserBoardgame;
import com.example.blue_hawk.domain.repository.IBoardgameRepository;
import com.example.blue_hawk.domain.repository.ICategoryRepository;
import com.example.blue_hawk.domain.repository.IUserBoardGameRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * ADMIN cadastra direto no catálogo (APPROVED). Usuário comum cadastra um jogo que possui:
 * ele entra como PENDING e já vai para a estante de quem cadastrou, aguardando aprovação para aparecer na wiki.
 * A foto de capa, se houver, é enviada depois em POST /boardgames/{id}/cover.
 */
@Service
public class CreateBoardgameUseCaseImpl implements CreateBoardgameUseCase {

    private final IBoardgameRepository boardgameRepository;
    private final IUserBoardGameRepository userBoardGameRepository;
    private final ICategoryRepository categoryRepository;

    public CreateBoardgameUseCaseImpl(IBoardgameRepository boardgameRepository,
                                      IUserBoardGameRepository userBoardGameRepository,
                                      ICategoryRepository categoryRepository) {
        this.boardgameRepository = boardgameRepository;
        this.userBoardGameRepository = userBoardGameRepository;
        this.categoryRepository = categoryRepository;
    }

    @Override
    @Transactional
    public CreateBoardgameOutput handle(CreateBoardgameCommand command) {
        CurrentUser currentUser = command.currentUser();
        if (currentUser == null || !currentUser.authenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        BoardgameValidation.validate(command.name(), command.description(), command.releaseYear(),
                command.playerNumber(), command.playTime());
        UUID categoryId = BoardgameValidation.requireCategory(categoryRepository, command.categoryId());
        String coverPreset = BoardgameValidation.normalizeCoverPreset(command.coverPreset());

        Boardgame boardgame = currentUser.admin()
                ? new Boardgame(command.name().trim(), command.description().trim(),
                        command.releaseYear(), command.playerNumber(), command.playTime())
                : Boardgame.submittedBy(currentUser.userId(), command.name().trim(), command.description().trim(),
                        command.releaseYear(), command.playerNumber(), command.playTime());
        boardgame.setSubmittedBy(currentUser.userId());
        boardgame.setCategoryId(categoryId);
        boardgame.setCoverPreset(coverPreset);
        Boardgame saved = boardgameRepository.save(boardgame);

        if (currentUser.admin()) {
            return CreateBoardgameOutput.from(saved, null);
        }
        UserBoardgame copy = userBoardGameRepository.save(
                new UserBoardgame(currentUser.userId().toString(), saved.getId().toString()));
        return CreateBoardgameOutput.from(saved, copy.getId().toString());
    }
}

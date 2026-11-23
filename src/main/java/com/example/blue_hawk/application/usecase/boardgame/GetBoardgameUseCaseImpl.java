package com.example.blue_hawk.application.usecase.boardgame;

import com.example.blue_hawk.application.dto.auth.CurrentUser;
import com.example.blue_hawk.application.dto.boardgame.GetBoardgameOutput;
import com.example.blue_hawk.application.dto.boardgame.GetBoardgameQuery;
import com.example.blue_hawk.domain.entity.boardgame.Boardgame;
import com.example.blue_hawk.domain.repository.IBoardgameLikeRepository;
import com.example.blue_hawk.domain.repository.IBoardgameRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
public class GetBoardgameUseCaseImpl implements GetBoardgameUseCase {

    private final IBoardgameRepository boardgameRepository;
    private final IBoardgameLikeRepository likeRepository;

    public GetBoardgameUseCaseImpl(IBoardgameRepository boardgameRepository, IBoardgameLikeRepository likeRepository) {
        this.boardgameRepository = boardgameRepository;
        this.likeRepository = likeRepository;
    }

    @Override
    public GetBoardgameOutput handle(GetBoardgameQuery query) {
        CurrentUser currentUser = query.currentUser() != null ? query.currentUser() : CurrentUser.anonymous();
        Boardgame boardgame = boardgameRepository
                .findById(UUID.fromString(query.id()))
                // Jogo não aprovado de outra pessoa responde como inexistente, para não expor a fila
                .filter(bg -> bg.isVisibleTo(currentUser.userId(), currentUser.admin()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Boardgame not found"));

        if (currentUser.authenticated()) {
            boardgame.setLikedByMe(likeRepository.isLiked(boardgame.getId(), currentUser.userId()));
        }
        return GetBoardgameOutput.from(boardgame);
    }
}

package com.example.blue_hawk.application.usecase.boardgame;

import com.example.blue_hawk.application.dto.auth.CurrentUser;
import com.example.blue_hawk.domain.entity.boardgame.Boardgame;
import com.example.blue_hawk.domain.repository.IBoardgameLikeRepository;
import com.example.blue_hawk.domain.repository.IBoardgameRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
public class LikeBoardgameUseCaseImpl implements LikeBoardgameUseCase {

    private final IBoardgameRepository boardgameRepository;
    private final IBoardgameLikeRepository likeRepository;

    public LikeBoardgameUseCaseImpl(IBoardgameRepository boardgameRepository, IBoardgameLikeRepository likeRepository) {
        this.boardgameRepository = boardgameRepository;
        this.likeRepository = likeRepository;
    }

    @Override
    public LikeOutput like(String boardgameId, CurrentUser currentUser) {
        Boardgame game = likeable(boardgameId, currentUser);
        likeRepository.like(game.getId(), currentUser.userId());
        return output(game.getId(), true);
    }

    @Override
    public LikeOutput unlike(String boardgameId, CurrentUser currentUser) {
        Boardgame game = likeable(boardgameId, currentUser);
        likeRepository.unlike(game.getId(), currentUser.userId());
        return output(game.getId(), false);
    }

    /** Só jogos do catálogo (aprovados) podem ser curtidos, e só por quem está logado */
    private Boardgame likeable(String boardgameId, CurrentUser currentUser) {
        if (currentUser == null || !currentUser.authenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        UUID id;
        try {
            id = UUID.fromString(boardgameId);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Boardgame not found");
        }
        return boardgameRepository.findById(id)
                .filter(Boardgame::isApproved)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Boardgame not found"));
    }

    private LikeOutput output(UUID boardgameId, boolean likedByMe) {
        return new LikeOutput(boardgameId.toString(), likeRepository.countFor(boardgameId), likedByMe);
    }
}

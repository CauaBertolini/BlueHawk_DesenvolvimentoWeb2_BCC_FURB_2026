package com.example.blue_hawk.application.usecase.boardgame;

import com.example.blue_hawk.application.dto.auth.CurrentUser;

/** Curtir/descurtir um jogo da Wiki. Idempotente: curtir duas vezes não conta duas curtidas. */
public interface LikeBoardgameUseCase {

    record LikeOutput(String boardgameId, int likeCount, boolean likedByMe) {
    }

    LikeOutput like(String boardgameId, CurrentUser currentUser);

    LikeOutput unlike(String boardgameId, CurrentUser currentUser);
}

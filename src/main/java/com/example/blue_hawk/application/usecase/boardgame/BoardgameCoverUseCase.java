package com.example.blue_hawk.application.usecase.boardgame;

import com.example.blue_hawk.application.dto.auth.CurrentUser;
import com.example.blue_hawk.application.dto.boardgame.GetBoardgameOutput;
import com.example.blue_hawk.domain.repository.IBoardgameCoverRepository.CoverImage;

/** Foto de capa enviada para um boardgame. */
public interface BoardgameCoverUseCase {

    /** Envia (ou troca) a foto. Quem pode: ADMIN ou o autor enquanto o jogo não foi aprovado. */
    GetBoardgameOutput upload(String boardgameId, byte[] data, CurrentUser currentUser);

    /** Remove a foto: a capa volta a ser a arte escolhida (ou a automática). */
    GetBoardgameOutput remove(String boardgameId, CurrentUser currentUser);

    /** Lê a foto (pública: a URL só é conhecida por quem já vê o jogo). */
    CoverImage get(String boardgameId);
}

package com.example.blue_hawk.application.dto.boardgame;

import com.example.blue_hawk.application.dto.auth.CurrentUser;

/** {@code coverPreset}: arte do site escolhida como capa; null = automática (ou foto enviada depois). */
public record CreateBoardgameCommand(
        String name,
        String description,
        Integer releaseYear,
        Integer playerNumber,
        Integer playTime,
        String categoryId,
        String coverPreset,
        CurrentUser currentUser
) {
}

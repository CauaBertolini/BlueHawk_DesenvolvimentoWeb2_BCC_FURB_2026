package com.example.blue_hawk.presentation.boardgame;

/**
 * {@code categoryId}: obrigatório (GET /categories).
 * {@code coverPreset}: arte do site para a capa (ex.: "waves-1"); null = automática.
 * A foto de capa vai à parte, em POST /boardgames/{id}/cover.
 */
public record BoardgameRequest(
        String name,
        String description,
        Integer releaseYear,
        Integer playerNumber,
        Integer playTime,
        String categoryId,
        String coverPreset
) {
}

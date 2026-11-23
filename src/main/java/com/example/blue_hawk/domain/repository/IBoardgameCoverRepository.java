package com.example.blue_hawk.domain.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Foto de capa enviada para um boardgame (tabela board_game_cover). */
public interface IBoardgameCoverRepository {

    record CoverImage(String contentType, byte[] data, Instant updatedAt) {
    }

    void save(UUID boardgameId, String contentType, byte[] data, Instant updatedAt);

    Optional<CoverImage> findByBoardgameId(UUID boardgameId);

    void deleteByBoardgameId(UUID boardgameId);
}

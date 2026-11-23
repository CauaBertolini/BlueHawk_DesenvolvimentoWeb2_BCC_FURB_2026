package com.example.blue_hawk.domain.repository;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/** Curtidas de jogos (tabela board_game_like), mantendo o contador board_game.like_count junto. */
public interface IBoardgameLikeRepository {

    /** Curte; devolve false se já estava curtido (nada muda) */
    boolean like(UUID boardgameId, UUID userId);

    /** Descurte; devolve false se não estava curtido (nada muda) */
    boolean unlike(UUID boardgameId, UUID userId);

    boolean isLiked(UUID boardgameId, UUID userId);

    /** Quais destes jogos o usuário curtiu (para marcar o coração nas listagens) */
    Set<UUID> findLikedAmong(UUID userId, Collection<UUID> boardgameIds);

    int countFor(UUID boardgameId);
}

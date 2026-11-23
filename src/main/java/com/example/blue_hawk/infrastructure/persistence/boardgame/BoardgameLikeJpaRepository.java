package com.example.blue_hawk.infrastructure.persistence.boardgame;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface BoardgameLikeJpaRepository extends JpaRepository<BoardgameLikeJpaEntity, BoardgameLikeJpaEntity.Key> {

    /** Idempotente e seguro contra cliques simultâneos: devolve 1 se curtiu agora, 0 se já estava curtido */
    @Modifying
    @Query(value = """
            INSERT INTO board_game_like (board_game_id, user_id, created_at)
            VALUES (:boardgameId, :userId, CURRENT_TIMESTAMP)
            ON CONFLICT (board_game_id, user_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("boardgameId") String boardgameId, @Param("userId") String userId);

    /** Devolve 1 se descurtiu agora, 0 se não estava curtido */
    @Modifying
    @Query(value = "DELETE FROM board_game_like WHERE board_game_id = :boardgameId AND user_id = :userId",
            nativeQuery = true)
    int deleteIfPresent(@Param("boardgameId") String boardgameId, @Param("userId") String userId);

    @Query("select l.boardgameId from BoardgameLikeJpaEntity l where l.userId = :userId and l.boardgameId in :ids")
    List<UUID> findLikedAmong(@Param("userId") UUID userId, @Param("ids") Collection<UUID> ids);

    boolean existsByBoardgameIdAndUserId(UUID boardgameId, UUID userId);
}

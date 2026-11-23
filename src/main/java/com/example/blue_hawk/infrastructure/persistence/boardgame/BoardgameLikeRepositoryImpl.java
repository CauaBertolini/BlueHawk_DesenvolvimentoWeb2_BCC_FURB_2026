package com.example.blue_hawk.infrastructure.persistence.boardgame;

import com.example.blue_hawk.domain.repository.IBoardgameLikeRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Repository
public class BoardgameLikeRepositoryImpl implements IBoardgameLikeRepository {

    private final BoardgameLikeJpaRepository likeJpa;
    private final BoardgameJpaRepository boardgameJpa;

    public BoardgameLikeRepositoryImpl(BoardgameLikeJpaRepository likeJpa, BoardgameJpaRepository boardgameJpa) {
        this.likeJpa = likeJpa;
        this.boardgameJpa = boardgameJpa;
    }

    /** Curtida e contador na mesma transação: o contador só muda se a linha foi de fato criada */
    @Override
    @Transactional
    public boolean like(UUID boardgameId, UUID userId) {
        boolean created = likeJpa.insertIfAbsent(boardgameId.toString(), userId.toString()) == 1;
        if (created) boardgameJpa.addLikes(boardgameId.toString(), 1);
        return created;
    }

    @Override
    @Transactional
    public boolean unlike(UUID boardgameId, UUID userId) {
        boolean removed = likeJpa.deleteIfPresent(boardgameId.toString(), userId.toString()) == 1;
        if (removed) boardgameJpa.addLikes(boardgameId.toString(), -1);
        return removed;
    }

    @Override
    public boolean isLiked(UUID boardgameId, UUID userId) {
        return likeJpa.existsByBoardgameIdAndUserId(boardgameId, userId);
    }

    @Override
    public Set<UUID> findLikedAmong(UUID userId, Collection<UUID> boardgameIds) {
        if (userId == null || boardgameIds.isEmpty()) return Set.of();
        return new HashSet<>(likeJpa.findLikedAmong(userId, boardgameIds));
    }

    @Override
    public int countFor(UUID boardgameId) {
        Integer count = boardgameJpa.findLikeCount(boardgameId.toString());
        return count != null ? count : 0;
    }
}

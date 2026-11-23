package com.example.blue_hawk.infrastructure.persistence.boardgame;

import com.example.blue_hawk.domain.repository.IBoardgameCoverRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

@Repository
public class BoardgameCoverRepositoryImpl implements IBoardgameCoverRepository {

    private final BoardgameCoverJpaRepository coverJpaRepository;

    public BoardgameCoverRepositoryImpl(BoardgameCoverJpaRepository coverJpaRepository) {
        this.coverJpaRepository = coverJpaRepository;
    }

    @Override
    public void save(UUID boardgameId, String contentType, byte[] data, Instant updatedAt) {
        coverJpaRepository.save(new BoardgameCoverJpaEntity(
                boardgameId, contentType, data, LocalDateTime.ofInstant(updatedAt, ZoneOffset.UTC)));
    }

    @Override
    public Optional<CoverImage> findByBoardgameId(UUID boardgameId) {
        return coverJpaRepository.findById(boardgameId)
                .map(e -> new CoverImage(e.getContentType(), e.getData(), e.getUpdatedAt().toInstant(ZoneOffset.UTC)));
    }

    @Override
    public void deleteByBoardgameId(UUID boardgameId) {
        if (coverJpaRepository.existsById(boardgameId)) {
            coverJpaRepository.deleteById(boardgameId);
        }
    }
}

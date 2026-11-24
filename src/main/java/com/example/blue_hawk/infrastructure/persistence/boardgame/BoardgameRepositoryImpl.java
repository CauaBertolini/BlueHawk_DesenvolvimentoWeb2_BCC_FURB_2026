package com.example.blue_hawk.infrastructure.persistence.boardgame;

import com.example.blue_hawk.domain.entity.boardgame.ApprovalStatus;
import com.example.blue_hawk.domain.entity.boardgame.Boardgame;
import com.example.blue_hawk.domain.repository.IBoardgameRepository;
import com.example.blue_hawk.infrastructure.persistence.category.CategoryJpaRepository;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class BoardgameRepositoryImpl implements IBoardgameRepository {
    private final BoardgameJpaRepository boardgameJpaRepository;
    private final CategoryJpaRepository categoryJpaRepository;

    public BoardgameRepositoryImpl(BoardgameJpaRepository boardgameJpaRepository,
                                   CategoryJpaRepository categoryJpaRepository) {
        this.boardgameJpaRepository = boardgameJpaRepository;
        this.categoryJpaRepository = categoryJpaRepository;
    }

    @Override
    public Boardgame save(Boardgame boardgame) {
        BoardgameJpaEntity jpaEntity = toJpaEntity(boardgame);
        BoardgameJpaEntity saved = boardgameJpaRepository.save(jpaEntity);
        return toDomain(saved);
    }

    @Override
    public Optional<Boardgame> findById(UUID boardgameId) {
        return boardgameJpaRepository.findById(boardgameId)
                .map(this::toDomain);
    }

    @Override
    public Page<Boardgame> findAll(String name, ApprovalStatus approvalStatus, int page, int size) {
        return findAll(name, approvalStatus, false, page, size);
    }

    @Override
    public Page<Boardgame> findAll(String name, ApprovalStatus approvalStatus, boolean mostLikedFirst, int page,
                                   int size) {
        // Ordem estável (nome como desempate): sem ela a paginação pode repetir/pular jogos entre páginas
        Sort sort = mostLikedFirst
                ? Sort.by("likeCount").descending().and(Sort.by("name").ascending())
                : Sort.by("name").ascending();
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<BoardgameJpaEntity> entities = (name != null && !name.isBlank())
                ? boardgameJpaRepository.findByNameContainingIgnoreCaseAndApprovalStatus(name.trim(), approvalStatus, pageable)
                : boardgameJpaRepository.findByApprovalStatus(approvalStatus, pageable);
        return entities.map(this::toDomain);
    }

    @Override
    public Page<Boardgame> findAllInCategory(String name, String category, ApprovalStatus approvalStatus,
                                             boolean mostLikedFirst, int page, int size) {
        Sort sort = mostLikedFirst
                ? Sort.by("likeCount").descending().and(Sort.by("name").ascending())
                : Sort.by("name").ascending();
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<BoardgameJpaEntity> entities = (name != null && !name.isBlank())
                ? boardgameJpaRepository.findByNameContainingIgnoreCaseAndCategory_NameIgnoreCaseAndApprovalStatus(
                        name.trim(), category.trim(), approvalStatus, pageable)
                : boardgameJpaRepository.findByCategory_NameIgnoreCaseAndApprovalStatus(category.trim(), approvalStatus,
                        pageable);
        return entities.map(this::toDomain);
    }

    @Override
    public void deleteById(String boardgameId) {
        boardgameJpaRepository.deleteById(UUID.fromString(boardgameId));
    }

    private BoardgameJpaEntity toJpaEntity(Boardgame domain) {
        BoardgameJpaEntity entity = new BoardgameJpaEntity();
        entity.setId(domain.getId());
        entity.setName(domain.getName());
        entity.setDescription(domain.getDescription());
        entity.setReleaseYear(domain.getReleaseYear());
        entity.setPlayerNumber(domain.getPlayerNumber());
        entity.setPlayTime(domain.getPlayTime());
        entity.setApprovalStatus(domain.getApprovalStatus());
        entity.setSubmittedBy(domain.getSubmittedBy());
        entity.setReviewNote(domain.getReviewNote());
        // findById (e não getReferenceById): a resposta usa o nome da categoria fora de transação
        entity.setCategory(domain.getCategoryId() != null
                ? categoryJpaRepository.findById(domain.getCategoryId()).orElse(null)
                : null);
        entity.setCoverPreset(domain.getCoverPreset());
        entity.setCoverImageUpdatedAt(domain.getCoverImageUpdatedAt() != null
                ? LocalDateTime.ofInstant(domain.getCoverImageUpdatedAt(), ZoneOffset.UTC)
                : null);
        return entity;
    }

    private Boardgame toDomain(BoardgameJpaEntity entity) {
        Boardgame boardgame = new Boardgame(
                entity.getId(),
                entity.getName(),
                entity.getDescription(),
                entity.getReleaseYear(),
                entity.getPlayerNumber(),
                entity.getPlayTime()
        );
        boardgame.setApprovalStatus(entity.getApprovalStatus());
        boardgame.setSubmittedBy(entity.getSubmittedBy());
        boardgame.setReviewNote(entity.getReviewNote());
        if (entity.getCategory() != null) {
            boardgame.setCategoryId(entity.getCategory().getId());
            boardgame.setCategoryName(entity.getCategory().getName());
        }
        boardgame.setLikeCount(entity.getLikeCount() != null ? entity.getLikeCount() : 0);
        boardgame.setCoverPreset(entity.getCoverPreset());
        boardgame.setCoverImageUpdatedAt(entity.getCoverImageUpdatedAt() != null
                ? entity.getCoverImageUpdatedAt().toInstant(ZoneOffset.UTC)
                : null);
        return boardgame;
    }
}

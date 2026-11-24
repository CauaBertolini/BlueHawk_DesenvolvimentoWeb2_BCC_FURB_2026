package com.example.blue_hawk.infrastructure.persistence.boardgame;

import com.example.blue_hawk.domain.entity.boardgame.ApprovalStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface BoardgameJpaRepository extends JpaRepository<BoardgameJpaEntity, UUID> {
    Page<BoardgameJpaEntity> findByApprovalStatus(ApprovalStatus approvalStatus, Pageable pageable);

    Page<BoardgameJpaEntity> findByNameContainingIgnoreCaseAndApprovalStatus(String name, ApprovalStatus approvalStatus,
                                                                             Pageable pageable);

    /** Filtro por categoria (nome exato, sem diferenciar maiúsculas) */
    Page<BoardgameJpaEntity> findByCategory_NameIgnoreCaseAndApprovalStatus(String category,
                                                                            ApprovalStatus approvalStatus,
                                                                            Pageable pageable);

    Page<BoardgameJpaEntity> findByNameContainingIgnoreCaseAndCategory_NameIgnoreCaseAndApprovalStatus(
            String name, String category, ApprovalStatus approvalStatus, Pageable pageable);

    /** Soma (ou subtrai) curtidas direto no banco, sem ler-e-gravar o contador */
    @Modifying
    @Query(value = "UPDATE board_game SET like_count = GREATEST(like_count + :delta, 0) WHERE id = :id",
            nativeQuery = true)
    void addLikes(@Param("id") String id, @Param("delta") int delta);

    @Query(value = "SELECT like_count FROM board_game WHERE id = :id", nativeQuery = true)
    Integer findLikeCount(@Param("id") String id);
}

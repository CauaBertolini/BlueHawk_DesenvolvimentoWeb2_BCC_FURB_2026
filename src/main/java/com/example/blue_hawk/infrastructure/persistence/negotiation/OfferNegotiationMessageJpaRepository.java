package com.example.blue_hawk.infrastructure.persistence.negotiation;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** A busca devolve da mais recente para a mais antiga (o limite pega as últimas); o RepositoryImpl inverte. */
@Repository
public interface OfferNegotiationMessageJpaRepository extends JpaRepository<OfferNegotiationMessageJpaEntity, UUID> {

    @Query("""
            select m from OfferNegotiationMessageJpaEntity m
            where m.negotiationId = :negotiationId and m.createdAt > :after
            order by m.createdAt desc, m.id desc
            """)
    List<OfferNegotiationMessageJpaEntity> findNewestFirst(@Param("negotiationId") UUID negotiationId,
                                                           @Param("after") LocalDateTime after,
                                                           Pageable pageable);
}

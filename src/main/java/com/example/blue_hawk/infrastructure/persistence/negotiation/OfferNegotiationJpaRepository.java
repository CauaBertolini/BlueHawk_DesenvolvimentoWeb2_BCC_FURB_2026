package com.example.blue_hawk.infrastructure.persistence.negotiation;

import com.example.blue_hawk.domain.entity.negotiation.OfferNegotiation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OfferNegotiationJpaRepository extends JpaRepository<OfferNegotiationJpaEntity, UUID> {

    Optional<OfferNegotiationJpaEntity> findByOfferIdAndBuyerId(UUID offerId, UUID buyerId);

    List<OfferNegotiationJpaEntity> findByOfferIdAndStatus(UUID offerId, OfferNegotiation.Status status);

    /** Comprador, ou vendedor: o dono da cópia (user_board_game) anunciada */
    @Query("""
            select n from OfferNegotiationJpaEntity n
            where n.buyerId = :userId
               or n.offerId in (
                    select o.id from OfferJpaEntity o, UserBoardgameJpaEntity c
                    where o.userBoardgameId = c.id and c.userId = :userId)
            """)
    List<OfferNegotiationJpaEntity> findInvolving(@Param("userId") UUID userId);
}

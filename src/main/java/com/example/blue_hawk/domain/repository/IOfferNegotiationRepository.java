package com.example.blue_hawk.domain.repository;

import com.example.blue_hawk.domain.entity.negotiation.OfferNegotiation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IOfferNegotiationRepository {

    OfferNegotiation save(OfferNegotiation negotiation);

    Optional<OfferNegotiation> findById(UUID id);

    /** A negociação do comprador no anúncio (uma por comprador por anúncio) */
    Optional<OfferNegotiation> findByOfferAndBuyer(UUID offerId, UUID buyerId);

    /** Negociações do anúncio com o status dado */
    List<OfferNegotiation> findByOfferAndStatus(UUID offerId, OfferNegotiation.Status status);

    /** Negociações em que o usuário é o comprador ou o vendedor (dono da cópia anunciada) */
    List<OfferNegotiation> findInvolving(UUID userId);
}

package com.example.blue_hawk.domain.repository;

import com.example.blue_hawk.domain.entity.offer.Offer;
import com.example.blue_hawk.domain.entity.offer.OfferStatus;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IOfferRepository {

    Offer save(Offer offer);

    Optional<Offer> findById(UUID id);

    Page<Offer> findAll(UUID userId, UUID boardgameId, OfferStatus status, int page, int size);

    void deleteById(UUID id);

    /** Anúncios ativos para a vitrine da Loja: mais recentes primeiro, ou mais baratos primeiro */
    List<Offer> findActiveForStore(boolean cheapestFirst, int limit);

    boolean existsById(UUID id);
}

package com.example.blue_hawk.infrastructure.persistence.negotiation;

import com.example.blue_hawk.domain.entity.negotiation.OfferNegotiation;
import com.example.blue_hawk.domain.repository.IOfferNegotiationRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Datas gravadas em UTC (colunas TIMESTAMP sem fuso). */
@Repository
public class OfferNegotiationRepositoryImpl implements IOfferNegotiationRepository {

    private final OfferNegotiationJpaRepository jpa;

    public OfferNegotiationRepositoryImpl(OfferNegotiationJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public OfferNegotiation save(OfferNegotiation n) {
        return toDomain(jpa.save(new OfferNegotiationJpaEntity(n.getId(), n.getOfferId(), n.getBuyerId(),
                n.getStatus(), n.getAgreedPrice(), utc(n.getCreatedAt()), n.getCreatedBy(), utc(n.getUpdatedAt()),
                n.getUpdatedBy())));
    }

    @Override
    public Optional<OfferNegotiation> findById(UUID id) {
        return jpa.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<OfferNegotiation> findByOfferAndBuyer(UUID offerId, UUID buyerId) {
        return jpa.findByOfferIdAndBuyerId(offerId, buyerId).map(this::toDomain);
    }

    @Override
    public List<OfferNegotiation> findByOfferAndStatus(UUID offerId, OfferNegotiation.Status status) {
        return jpa.findByOfferIdAndStatus(offerId, status).stream().map(this::toDomain).toList();
    }

    @Override
    public List<OfferNegotiation> findInvolving(UUID userId) {
        return jpa.findInvolving(userId).stream().map(this::toDomain).toList();
    }

    private OfferNegotiation toDomain(OfferNegotiationJpaEntity e) {
        return new OfferNegotiation(e.getId(), e.getOfferId(), e.getBuyerId(), e.getStatus(), e.getAgreedPrice(),
                instant(e.getCreatedAt()), e.getCreatedBy(), instant(e.getUpdatedAt()), e.getUpdatedBy());
    }

    private static LocalDateTime utc(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static Instant instant(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.toInstant(ZoneOffset.UTC);
    }
}

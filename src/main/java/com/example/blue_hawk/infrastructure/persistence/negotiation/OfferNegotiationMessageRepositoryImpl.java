package com.example.blue_hawk.infrastructure.persistence.negotiation;

import com.example.blue_hawk.domain.entity.negotiation.OfferNegotiationMessage;
import com.example.blue_hawk.domain.repository.IOfferNegotiationMessageRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Datas gravadas em UTC (colunas TIMESTAMP sem fuso). */
@Repository
public class OfferNegotiationMessageRepositoryImpl implements IOfferNegotiationMessageRepository {

    /** Sem {@code after}: qualquer data serve */
    private static final LocalDateTime BEGINNING = LocalDateTime.of(1970, 1, 1, 0, 0);

    private final OfferNegotiationMessageJpaRepository jpa;

    public OfferNegotiationMessageRepositoryImpl(OfferNegotiationMessageJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public OfferNegotiationMessage save(OfferNegotiationMessage m) {
        return toDomain(jpa.save(new OfferNegotiationMessageJpaEntity(m.getId(), m.getNegotiationId(),
                m.getSenderId(), m.getBody(), m.getProposedPrice(), utc(m.getCreatedAt()), m.getCreatedBy(),
                utc(m.getUpdatedAt()), m.getUpdatedBy())));
    }

    @Override
    public Optional<OfferNegotiationMessage> findById(UUID id) {
        return jpa.findById(id).map(this::toDomain);
    }

    @Override
    public List<OfferNegotiationMessage> find(UUID negotiationId, Instant after, int limit) {
        LocalDateTime cursor = after == null ? BEGINNING : utc(after);
        List<OfferNegotiationMessage> messages = new ArrayList<>(jpa.findNewestFirst(negotiationId, cursor,
                PageRequest.of(0, limit)).stream().map(this::toDomain).toList());
        Collections.reverse(messages);
        return messages;
    }

    @Override
    public Optional<OfferNegotiationMessage> findLast(UUID negotiationId) {
        return jpa.findNewestFirst(negotiationId, BEGINNING, PageRequest.of(0, 1)).stream()
                .findFirst()
                .map(this::toDomain);
    }

    private OfferNegotiationMessage toDomain(OfferNegotiationMessageJpaEntity e) {
        return new OfferNegotiationMessage(e.getId(), e.getNegotiationId(), e.getSenderId(), e.getBody(),
                e.getProposedPrice(), instant(e.getCreatedAt()), e.getCreatedBy(), instant(e.getUpdatedAt()),
                e.getUpdatedBy());
    }

    private static LocalDateTime utc(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static Instant instant(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.toInstant(ZoneOffset.UTC);
    }
}

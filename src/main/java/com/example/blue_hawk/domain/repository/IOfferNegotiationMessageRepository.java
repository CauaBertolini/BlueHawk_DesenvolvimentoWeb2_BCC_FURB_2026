package com.example.blue_hawk.domain.repository;

import com.example.blue_hawk.domain.entity.negotiation.OfferNegotiationMessage;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IOfferNegotiationMessageRepository {

    OfferNegotiationMessage save(OfferNegotiationMessage message);

    Optional<OfferNegotiationMessage> findById(UUID id);

    /**
     * As {@code limit} mensagens mais recentes da negociação, em ordem cronológica.
     * {@code after} (opcional): só as estritamente posteriores.
     */
    List<OfferNegotiationMessage> find(UUID negotiationId, Instant after, int limit);

    /** Última mensagem da negociação */
    Optional<OfferNegotiationMessage> findLast(UUID negotiationId);
}

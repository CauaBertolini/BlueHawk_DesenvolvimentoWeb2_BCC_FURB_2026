package com.example.blue_hawk.domain.entity.negotiation;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/** Mensagem de uma negociação, opcionalmente com uma proposta de preço */
public class OfferNegotiationMessage {

    private final UUID id;
    private final UUID negotiationId;
    private final UUID senderId;
    private final String body;
    /** null = mensagem sem proposta */
    private final BigDecimal proposedPrice;
    private final Instant createdAt;
    private final UUID createdBy;
    private final Instant updatedAt;
    private final UUID updatedBy;

    public OfferNegotiationMessage(UUID id, UUID negotiationId, UUID senderId, String body, BigDecimal proposedPrice,
                                   Instant createdAt, UUID createdBy, Instant updatedAt, UUID updatedBy) {
        this.id = id;
        this.negotiationId = negotiationId;
        this.senderId = senderId;
        this.body = body;
        this.proposedPrice = proposedPrice;
        this.createdAt = createdAt;
        this.createdBy = createdBy;
        this.updatedAt = updatedAt;
        this.updatedBy = updatedBy;
    }

    /**
     * Data truncada em milissegundos: o cliente usa o {@code createdAt} devolvido como cursor ({@code after}), e
     * assim o valor gravado no banco é exatamente o mesmo que ele recebeu.
     */
    public static OfferNegotiationMessage send(UUID negotiationId, UUID senderId, String body,
                                               BigDecimal proposedPrice) {
        return new OfferNegotiationMessage(UUID.randomUUID(), negotiationId, senderId, body, proposedPrice,
                Instant.now().truncatedTo(ChronoUnit.MILLIS), senderId, null, null);
    }

    public boolean hasProposal() {
        return proposedPrice != null;
    }

    public UUID getId() {
        return id;
    }

    public UUID getNegotiationId() {
        return negotiationId;
    }

    public UUID getSenderId() {
        return senderId;
    }

    public String getBody() {
        return body;
    }

    public BigDecimal getProposedPrice() {
        return proposedPrice;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public UUID getUpdatedBy() {
        return updatedBy;
    }
}

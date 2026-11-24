package com.example.blue_hawk.domain.entity.negotiation;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Negociação de um anúncio da Loja entre um comprador e o vendedor (dono da cópia anunciada). Uma por comprador
 * por anúncio. O vendedor aceita uma proposta de preço do comprador ({@code ACCEPTED}); as demais negociações
 * abertas do mesmo anúncio são encerradas ({@code CLOSED}).
 */
public class OfferNegotiation {

    public enum Status { OPEN, ACCEPTED, CLOSED }

    private final UUID id;
    private final UUID offerId;
    private final UUID buyerId;
    private Status status;
    private BigDecimal agreedPrice;
    private final Instant createdAt;
    private final UUID createdBy;
    private Instant updatedAt;
    private UUID updatedBy;

    public OfferNegotiation(UUID id, UUID offerId, UUID buyerId, Status status, BigDecimal agreedPrice,
                            Instant createdAt, UUID createdBy, Instant updatedAt, UUID updatedBy) {
        this.id = id;
        this.offerId = offerId;
        this.buyerId = buyerId;
        this.status = status;
        this.agreedPrice = agreedPrice;
        this.createdAt = createdAt;
        this.createdBy = createdBy;
        this.updatedAt = updatedAt;
        this.updatedBy = updatedBy;
    }

    /** {@code created_by} = o comprador, que inicia a negociação */
    public static OfferNegotiation start(UUID offerId, UUID buyerId) {
        return new OfferNegotiation(UUID.randomUUID(), offerId, buyerId, Status.OPEN, null, now(), buyerId, null,
                null);
    }

    /** O vendedor aceitou a proposta de {@code price} */
    public void accept(BigDecimal price, UUID sellerId) {
        this.status = Status.ACCEPTED;
        this.agreedPrice = price;
        touch(sellerId);
    }

    /** O anúncio foi vendido em outra negociação */
    public void close(UUID actorId) {
        this.status = Status.CLOSED;
        touch(actorId);
    }

    public boolean isOpen() {
        return status == Status.OPEN;
    }

    public boolean isBuyer(UUID userId) {
        return userId != null && userId.equals(buyerId);
    }

    private void touch(UUID actorId) {
        this.updatedAt = now();
        this.updatedBy = actorId;
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MILLIS);
    }

    public UUID getId() {
        return id;
    }

    public UUID getOfferId() {
        return offerId;
    }

    public UUID getBuyerId() {
        return buyerId;
    }

    public Status getStatus() {
        return status;
    }

    public BigDecimal getAgreedPrice() {
        return agreedPrice;
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

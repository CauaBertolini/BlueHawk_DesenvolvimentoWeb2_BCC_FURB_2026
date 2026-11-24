package com.example.blue_hawk.application.dto.negotiation;

import java.math.BigDecimal;

/** DTOs das negociações dos anúncios da Loja. Datas em ISO-8601 UTC; preços como números. */
public final class OfferNegotiationDtos {

    private OfferNegotiationDtos() {
    }

    /** {@code coverImageUrl}: caminho da foto enviada (relativo à API), ou null */
    public record GameView(String id, String name, String coverPreset, String coverImageUrl) {
    }

    public record OfferView(String id, BigDecimal price, String status, GameView game) {
    }

    public record PartyView(String id, String name) {
    }

    /** {@code agreedPrice}: só em ACCEPTED. {@code isSeller}: quem está vendo é o vendedor. */
    public record NegotiationView(String id, String status, BigDecimal agreedPrice, boolean isSeller,
                                  OfferView offer, PartyView buyer, PartyView seller) {
    }

    /** {@code proposedPrice} null = mensagem sem proposta */
    public record MessageView(String id, String senderId, String senderName, String body, BigDecimal proposedPrice,
                              String createdAt, boolean mine) {
    }
}

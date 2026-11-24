package com.example.blue_hawk.application.dto.notification;

/** DTOs das notificações. Datas em ISO-8601 UTC. */
public final class NotificationDtos {

    private NotificationDtos() {
    }

    public record ActorView(String id, String name) {
    }

    /** {@code cancelReason}: motivo do cancelamento informado pelo host (null se a partida não foi cancelada) */
    public record MatchView(String id, String gameName, String scheduledAt, String cancelReason) {
    }

    public record BoardgameView(String id, String name) {
    }

    /** Negociação de um anúncio da Loja (OFFER_MESSAGE / OFFER_ACCEPTED) */
    public record NegotiationView(String id, String gameName, String offerId) {
    }

    /**
     * {@code createdAt}: última atividade (a última mensagem agregada, ou a criação). {@code count}: mensagens
     * agregadas (1 nos demais tipos). {@code threadUserId}: só em MATCH_MESSAGE de conversa direta.
     * {@code negotiation}: só em OFFER_MESSAGE / OFFER_ACCEPTED. {@code directConversationId}: só em
     * DIRECT_MESSAGE (o {@code actor} é quem mandou a última mensagem).
     */
    public record NotificationView(String id, String type, boolean read, String createdAt, int count,
                                   ActorView actor, MatchView match, BoardgameView boardgame,
                                   String threadUserId, NegotiationView negotiation,
                                   String directConversationId) {
    }

    public record CountView(long count) {
    }
}

package com.example.blue_hawk.domain.entity.notification;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Notificação de um usuário (pedidos do Jogue, mensagens do chat, revisão de jogos enviados, negociações da Loja,
 * mensagens diretas).
 * Mensagens do chat e das negociações são agregadas: enquanto não lida, a mesma notificação conta as novas mensagens da conversa.
 */
public class Notification {

    public enum Type {
        JOIN_REQUEST_RECEIVED,
        JOIN_REQUEST_WITHDRAWN,
        JOIN_REQUEST_ACCEPTED,
        JOIN_REQUEST_REJECTED,
        MATCH_CANCELLED,
        MATCH_MESSAGE,
        BOARDGAME_APPROVED,
        BOARDGAME_REJECTED,
        /** Nova mensagem numa negociação da Loja (agregada como MATCH_MESSAGE) */
        OFFER_MESSAGE,
        /** O vendedor aceitou a proposta do comprador */
        OFFER_ACCEPTED,
        /** Nova mensagem numa conversa direta entre usuários (agregada como MATCH_MESSAGE) */
        DIRECT_MESSAGE
    }

    private final UUID id;
    /** Destinatário */
    private final UUID userId;
    private final Type type;
    private UUID actorId;
    private final UUID openMatchId;
    private final UUID boardgameId;
    /** Só em MATCH_MESSAGE: a outra pessoa da conversa direta (null = mesa) */
    private final UUID threadUserId;
    /** Só em OFFER_MESSAGE / OFFER_ACCEPTED: a negociação */
    private final UUID negotiationId;
    /** Só em DIRECT_MESSAGE: a conversa direta */
    private final UUID directConversationId;
    private int count;
    private Instant readAt;
    private final Instant createdAt;
    private final UUID createdBy;
    /** Última atividade (mensagem agregada). Marcar como lida não mexe aqui. */
    private Instant updatedAt;
    private UUID updatedBy;

    public Notification(UUID id, UUID userId, Type type, UUID actorId, UUID openMatchId, UUID boardgameId,
                        UUID threadUserId, UUID negotiationId, UUID directConversationId, int count,
                        Instant readAt, Instant createdAt,
                        UUID createdBy, Instant updatedAt, UUID updatedBy) {
        this.id = id;
        this.userId = userId;
        this.type = type;
        this.actorId = actorId;
        this.openMatchId = openMatchId;
        this.boardgameId = boardgameId;
        this.threadUserId = threadUserId;
        this.negotiationId = negotiationId;
        this.directConversationId = directConversationId;
        this.count = count;
        this.readAt = readAt;
        this.createdAt = createdAt;
        this.createdBy = createdBy;
        this.updatedAt = updatedAt;
        this.updatedBy = updatedBy;
    }

    /** {@code actorId} também é o {@code created_by} (null = sistema) */
    public static Notification create(UUID userId, Type type, UUID actorId, UUID openMatchId, UUID boardgameId,
                                      UUID threadUserId) {
        return new Notification(UUID.randomUUID(), userId, type, actorId, openMatchId, boardgameId, threadUserId,
                null, null, 1, null, now(), actorId, null, null);
    }

    /** Negociação da Loja (OFFER_MESSAGE / OFFER_ACCEPTED). {@code actorId} também é o {@code created_by}. */
    public static Notification createForNegotiation(UUID userId, Type type, UUID actorId, UUID negotiationId) {
        return new Notification(UUID.randomUUID(), userId, type, actorId, null, null, null, negotiationId, null, 1,
                null, now(), actorId, null, null);
    }

    /** Mensagem direta (DIRECT_MESSAGE). {@code senderId} é o ator e o {@code created_by}. */
    public static Notification createForDirect(UUID userId, UUID senderId, UUID directConversationId) {
        return new Notification(UUID.randomUUID(), userId, Type.DIRECT_MESSAGE, senderId, null, null, null, null,
                directConversationId, 1, null, now(), senderId, null, null);
    }

    /** Mais uma mensagem na mesma conversa ainda não lida: soma no contador e passa a mostrar o último remetente */
    public void aggregate(UUID senderId) {
        this.count++;
        this.actorId = senderId;
        this.updatedAt = now();
        this.updatedBy = senderId;
    }

    public void markRead() {
        if (readAt == null) this.readAt = now();
    }

    public boolean isRead() {
        return readAt != null;
    }

    /** Data mostrada e usada na ordenação: a última atividade */
    public Instant lastActivityAt() {
        return updatedAt != null ? updatedAt : createdAt;
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MILLIS);
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public Type getType() {
        return type;
    }

    public UUID getActorId() {
        return actorId;
    }

    public UUID getOpenMatchId() {
        return openMatchId;
    }

    public UUID getBoardgameId() {
        return boardgameId;
    }

    public UUID getThreadUserId() {
        return threadUserId;
    }

    public UUID getNegotiationId() {
        return negotiationId;
    }

    public UUID getDirectConversationId() {
        return directConversationId;
    }

    public int getCount() {
        return count;
    }

    public Instant getReadAt() {
        return readAt;
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

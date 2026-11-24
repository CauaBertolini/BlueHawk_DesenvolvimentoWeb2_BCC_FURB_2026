package com.example.blue_hawk.domain.entity.direct;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/** Mensagem de uma conversa direta entre usuários */
public class DirectMessage {

    private final UUID id;
    private final UUID conversationId;
    private final UUID senderId;
    private final String body;
    private final Instant createdAt;
    private final UUID createdBy;
    private final Instant updatedAt;
    private final UUID updatedBy;

    public DirectMessage(UUID id, UUID conversationId, UUID senderId, String body, Instant createdAt,
                         UUID createdBy, Instant updatedAt, UUID updatedBy) {
        this.id = id;
        this.conversationId = conversationId;
        this.senderId = senderId;
        this.body = body;
        this.createdAt = createdAt;
        this.createdBy = createdBy;
        this.updatedAt = updatedAt;
        this.updatedBy = updatedBy;
    }

    /**
     * Data truncada em milissegundos: o cliente usa o {@code createdAt} devolvido como cursor ({@code after}), e
     * assim o valor gravado no banco é exatamente o mesmo que ele recebeu.
     */
    public static DirectMessage send(UUID conversationId, UUID senderId, String body) {
        return new DirectMessage(UUID.randomUUID(), conversationId, senderId, body,
                Instant.now().truncatedTo(ChronoUnit.MILLIS), senderId, null, null);
    }

    public UUID getId() {
        return id;
    }

    public UUID getConversationId() {
        return conversationId;
    }

    public UUID getSenderId() {
        return senderId;
    }

    public String getBody() {
        return body;
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

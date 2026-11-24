package com.example.blue_hawk.domain.entity.openmatch;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Mensagem do chat de uma partida do Jogue. Sem destinatário é para a mesa toda (host + aceitos); com
 * destinatário é uma conversa direta, sempre entre o host e um participante.
 */
public class OpenMatchMessage {

    private final UUID id;
    private final UUID openMatchId;
    private final UUID senderId;
    /** null = mesa */
    private final UUID recipientId;
    private final String body;
    private final Instant createdAt;
    private final UUID createdBy;
    private final Instant updatedAt;
    private final UUID updatedBy;

    public OpenMatchMessage(UUID id, UUID openMatchId, UUID senderId, UUID recipientId, String body,
                            Instant createdAt, UUID createdBy, Instant updatedAt, UUID updatedBy) {
        this.id = id;
        this.openMatchId = openMatchId;
        this.senderId = senderId;
        this.recipientId = recipientId;
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
    public static OpenMatchMessage send(UUID openMatchId, UUID senderId, UUID recipientId, String body) {
        return new OpenMatchMessage(UUID.randomUUID(), openMatchId, senderId, recipientId, body,
                Instant.now().truncatedTo(ChronoUnit.MILLIS), senderId, null, null);
    }

    public boolean isTable() {
        return recipientId == null;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOpenMatchId() {
        return openMatchId;
    }

    public UUID getSenderId() {
        return senderId;
    }

    public UUID getRecipientId() {
        return recipientId;
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

package com.example.blue_hawk.domain.entity.openmatch;

import java.time.Instant;
import java.util.UUID;

/** Pedido de entrada numa partida, com mensagem para o host, que aceita ou recusa (estilo carona). */
public class OpenMatchRequest {

    public enum Status { PENDING, ACCEPTED, REJECTED, CANCELLED }

    private final UUID id;
    private final UUID openMatchId;
    private final UUID userId;
    private String message;
    private Status status;
    private String hostReply;
    private Instant createdAt;
    private Instant decidedAt;

    public OpenMatchRequest(UUID id, UUID openMatchId, UUID userId, String message, Status status, String hostReply,
                            Instant createdAt, Instant decidedAt) {
        this.id = id;
        this.openMatchId = openMatchId;
        this.userId = userId;
        this.message = message;
        this.status = status;
        this.hostReply = hostReply;
        this.createdAt = createdAt;
        this.decidedAt = decidedAt;
    }

    public static OpenMatchRequest create(UUID openMatchId, UUID userId, String message) {
        return new OpenMatchRequest(UUID.randomUUID(), openMatchId, userId, message, Status.PENDING, null,
                Instant.now(), null);
    }

    /** Reenvio depois de cancelar: reaproveita a linha (um pedido por pessoa por partida) */
    public void resend(String newMessage) {
        this.message = newMessage;
        this.status = Status.PENDING;
        this.hostReply = null;
        this.createdAt = Instant.now();
        this.decidedAt = null;
    }

    public void accept(String reply) {
        decide(Status.ACCEPTED, reply);
    }

    public void reject(String reply) {
        decide(Status.REJECTED, reply);
    }

    public void cancel() {
        this.status = Status.CANCELLED;
    }

    private void decide(Status decision, String reply) {
        this.status = decision;
        this.hostReply = reply;
        this.decidedAt = Instant.now();
    }

    public boolean isActive() {
        return status == Status.PENDING || status == Status.ACCEPTED;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOpenMatchId() {
        return openMatchId;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getMessage() {
        return message;
    }

    public Status getStatus() {
        return status;
    }

    public String getHostReply() {
        return hostReply;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }
}

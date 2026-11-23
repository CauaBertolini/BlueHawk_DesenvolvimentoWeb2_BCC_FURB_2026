package com.example.blue_hawk.domain.entity.openmatch;

import java.time.Instant;
import java.util.UUID;

/**
 * Partida anunciada no matchmaking ("Jogue"): um host oferece um jogo da estante dele numa cidade e horário,
 * e outros usuários pedem para entrar. Diferente de Match, que registra partidas já jogadas.
 */
public class OpenMatch {

    public enum Status { OPEN, CANCELLED }

    private UUID id;
    private UUID hostId;
    private UUID userBoardgameId;
    private String city;
    private Instant scheduledAt;
    /** Total de jogadores na mesa, contando o host */
    private int maxPlayers;
    private String notes;
    private Status status;
    private Instant createdAt;

    public OpenMatch(UUID id, UUID hostId, UUID userBoardgameId, String city, Instant scheduledAt, int maxPlayers,
                     String notes, Status status, Instant createdAt) {
        this.id = id;
        this.hostId = hostId;
        this.userBoardgameId = userBoardgameId;
        this.city = city;
        this.scheduledAt = scheduledAt;
        this.maxPlayers = maxPlayers;
        this.notes = notes;
        this.status = status;
        this.createdAt = createdAt;
    }

    public static OpenMatch host(UUID hostId, UUID userBoardgameId, String city, Instant scheduledAt, int maxPlayers,
                                 String notes) {
        return new OpenMatch(UUID.randomUUID(), hostId, userBoardgameId, city, scheduledAt, maxPlayers, notes,
                Status.OPEN, Instant.now());
    }

    public boolean isHostedBy(UUID userId) {
        return userId != null && userId.equals(hostId);
    }

    /** Aceita pedidos: aberta e ainda não começou */
    public boolean isAcceptingRequests(Instant now) {
        return status == Status.OPEN && scheduledAt.isAfter(now);
    }

    public void cancel() {
        this.status = Status.CANCELLED;
    }

    public UUID getId() {
        return id;
    }

    public UUID getHostId() {
        return hostId;
    }

    public UUID getUserBoardgameId() {
        return userBoardgameId;
    }

    public String getCity() {
        return city;
    }

    public Instant getScheduledAt() {
        return scheduledAt;
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }

    public String getNotes() {
        return notes;
    }

    public Status getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

package com.example.blue_hawk.application.dto.openmatch;

import com.example.blue_hawk.application.dto.auth.CurrentUser;

/** DTOs do matchmaking ("Jogue"). Datas em ISO-8601 UTC (ex.: 2026-12-05T22:00:00Z). */
public final class OpenMatchDtos {

    private OpenMatchDtos() {
    }

    /** Hospedar: um jogo da estante do host, onde e quando. {@code maxPlayers} null = máximo do jogo. */
    public record HostCommand(String userBoardgameId, String city, String scheduledAt, Integer maxPlayers,
                              String notes, CurrentUser currentUser) {
    }

    /** {@code mine}: só as partidas que o usuário hospeda ou em que pediu para entrar */
    public record ListQuery(String city, String boardgameId, boolean mine, CurrentUser currentUser) {
    }

    public record HostView(String id, String name) {
    }

    public record GameView(String id, String name, String category, String coverPreset, Long coverImageVersion,
                           Integer playerNumber, Integer playTime) {
    }

    /** O pedido de quem está vendo a partida (null se não pediu) */
    public record MyRequestView(String id, String status, String message, String hostReply) {
    }

    /**
     * {@code players}: host + aceitos. {@code pendingRequests} só vem para o host. {@code cancelReason}: motivo
     * informado pelo host (null se não cancelada).
     */
    public record OpenMatchView(String id, String status, String city, String scheduledAt, int maxPlayers,
                                int players, int seatsLeft, Integer pendingRequests, String notes,
                                HostView host, GameView game, boolean isHost, MyRequestView myRequest,
                                String cancelReason) {
    }

    /** Pedido visto pelo host */
    public record RequestView(String id, String userId, String userName, String message, String status,
                              String hostReply, String createdAt) {
    }
}

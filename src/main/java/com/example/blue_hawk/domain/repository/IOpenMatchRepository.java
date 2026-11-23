package com.example.blue_hawk.domain.repository;

import com.example.blue_hawk.domain.entity.openmatch.OpenMatch;
import com.example.blue_hawk.domain.entity.openmatch.OpenMatchRequest;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface IOpenMatchRepository {

    OpenMatch save(OpenMatch match);

    Optional<OpenMatch> findById(UUID id);

    /**
     * Partidas abertas a partir de {@code from}, da mais próxima para a mais distante.
     * {@code city} (trecho, sem diferenciar maiúsculas) e {@code boardgameId} são filtros opcionais.
     */
    List<OpenMatch> findUpcoming(Instant from, String city, UUID boardgameId, int limit);

    /** Partidas futuras que o usuário hospeda ou em que tem pedido ativo (pendente ou aceito) */
    List<OpenMatch> findUpcomingInvolving(UUID userId, Instant from);

    OpenMatchRequest saveRequest(OpenMatchRequest request);

    Optional<OpenMatchRequest> findRequestById(UUID requestId);

    Optional<OpenMatchRequest> findRequest(UUID openMatchId, UUID userId);

    List<OpenMatchRequest> findRequests(UUID openMatchId);

    long countAccepted(UUID openMatchId);

    long countPending(UUID openMatchId);

    /** Partidas já realizadas pelo usuário (host ou aceito, não canceladas), por id de boardgame */
    Map<UUID, Long> countPlayedByBoardgame(UUID userId, Instant now);
}

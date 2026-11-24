package com.example.blue_hawk.infrastructure.persistence.openmatch;

import com.example.blue_hawk.domain.entity.openmatch.OpenMatch;
import com.example.blue_hawk.domain.entity.openmatch.OpenMatchRequest;
import com.example.blue_hawk.domain.repository.IOpenMatchRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Datas gravadas em UTC (colunas TIMESTAMP sem fuso). */
@Repository
public class OpenMatchRepositoryImpl implements IOpenMatchRepository {

    private final OpenMatchJpaRepository matchJpa;
    private final OpenMatchRequestJpaRepository requestJpa;

    public OpenMatchRepositoryImpl(OpenMatchJpaRepository matchJpa, OpenMatchRequestJpaRepository requestJpa) {
        this.matchJpa = matchJpa;
        this.requestJpa = requestJpa;
    }

    @Override
    public OpenMatch save(OpenMatch m) {
        return toDomain(matchJpa.save(new OpenMatchJpaEntity(m.getId(), m.getHostId(), m.getUserBoardgameId(),
                m.getCity(), utc(m.getScheduledAt()), m.getMaxPlayers(), m.getNotes(), m.getStatus(),
                utc(m.getCreatedAt()), m.getCancelReason())));
    }

    @Override
    public Optional<OpenMatch> findById(UUID id) {
        return matchJpa.findById(id).map(this::toDomain);
    }

    @Override
    public List<OpenMatch> findUpcoming(Instant from, String city, UUID boardgameId, int limit) {
        String cityPattern = "%" + (city == null ? "" : city.trim().toLowerCase(Locale.ROOT)) + "%";
        PageRequest page = PageRequest.of(0, limit);
        List<OpenMatchJpaEntity> rows = boardgameId == null
                ? matchJpa.findUpcoming(OpenMatch.Status.OPEN, utc(from), cityPattern, page)
                : matchJpa.findUpcomingForBoardgame(OpenMatch.Status.OPEN, utc(from), cityPattern, boardgameId, page);
        return rows.stream().map(this::toDomain).toList();
    }

    @Override
    public List<OpenMatch> findUpcomingInvolving(UUID userId, Instant from) {
        return matchJpa.findUpcomingInvolving(userId, utc(from)).stream().map(this::toDomain).toList();
    }

    @Override
    public List<OpenMatch> findInvolvingSince(UUID userId, Instant from) {
        return matchJpa.findInvolvingSince(userId, utc(from)).stream().map(this::toDomain).toList();
    }

    @Override
    public OpenMatchRequest saveRequest(OpenMatchRequest r) {
        return toDomain(requestJpa.save(new OpenMatchRequestJpaEntity(r.getId(), r.getOpenMatchId(), r.getUserId(),
                r.getMessage(), r.getStatus(), r.getHostReply(), utc(r.getCreatedAt()), utc(r.getDecidedAt()))));
    }

    @Override
    public Optional<OpenMatchRequest> findRequestById(UUID requestId) {
        return requestJpa.findById(requestId).map(this::toDomain);
    }

    @Override
    public Optional<OpenMatchRequest> findRequest(UUID openMatchId, UUID userId) {
        return requestJpa.findByOpenMatchIdAndUserId(openMatchId, userId).map(this::toDomain);
    }

    @Override
    public List<OpenMatchRequest> findRequests(UUID openMatchId) {
        return requestJpa.findByOpenMatchIdOrderByCreatedAtAsc(openMatchId).stream().map(this::toDomain).toList();
    }

    @Override
    public long countAccepted(UUID openMatchId) {
        return requestJpa.countByOpenMatchIdAndStatus(openMatchId, OpenMatchRequest.Status.ACCEPTED);
    }

    @Override
    public long countPending(UUID openMatchId) {
        return requestJpa.countByOpenMatchIdAndStatus(openMatchId, OpenMatchRequest.Status.PENDING);
    }

    @Override
    public Map<UUID, Long> countPlayedByBoardgame(UUID userId, Instant now) {
        Map<UUID, Long> played = new HashMap<>();
        for (Object[] row : matchJpa.countPlayedByBoardgame(userId.toString(), utc(now))) {
            played.put(UUID.fromString(row[0].toString().trim()), ((Number) row[1]).longValue());
        }
        return played;
    }

    private OpenMatch toDomain(OpenMatchJpaEntity e) {
        return new OpenMatch(e.getId(), e.getHostId(), e.getUserBoardgameId(), e.getCity(), instant(e.getScheduledAt()),
                e.getMaxPlayers(), e.getNotes(), e.getStatus(), instant(e.getCreatedAt()), e.getCancelReason());
    }

    private OpenMatchRequest toDomain(OpenMatchRequestJpaEntity e) {
        return new OpenMatchRequest(e.getId(), e.getOpenMatchId(), e.getUserId(), e.getMessage(), e.getStatus(),
                e.getHostReply(), instant(e.getCreatedAt()), instant(e.getDecidedAt()));
    }

    private static LocalDateTime utc(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static Instant instant(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.toInstant(ZoneOffset.UTC);
    }
}

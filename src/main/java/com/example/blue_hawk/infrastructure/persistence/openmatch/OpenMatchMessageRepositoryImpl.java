package com.example.blue_hawk.infrastructure.persistence.openmatch;

import com.example.blue_hawk.domain.entity.openmatch.OpenMatchMessage;
import com.example.blue_hawk.domain.repository.IOpenMatchMessageRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Datas gravadas em UTC (colunas TIMESTAMP sem fuso). */
@Repository
public class OpenMatchMessageRepositoryImpl implements IOpenMatchMessageRepository {

    /** Sem {@code after}: qualquer data serve */
    private static final LocalDateTime BEGINNING = LocalDateTime.of(1970, 1, 1, 0, 0);

    private final OpenMatchMessageJpaRepository jpa;

    public OpenMatchMessageRepositoryImpl(OpenMatchMessageJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public OpenMatchMessage save(OpenMatchMessage m) {
        return toDomain(jpa.save(new OpenMatchMessageJpaEntity(m.getId(), m.getOpenMatchId(), m.getSenderId(),
                m.getRecipientId(), m.getBody(), utc(m.getCreatedAt()), m.getCreatedBy(), utc(m.getUpdatedAt()),
                m.getUpdatedBy())));
    }

    @Override
    public List<OpenMatchMessage> findTable(UUID openMatchId, Instant after, int limit) {
        return chronological(jpa.findTableNewestFirst(openMatchId, cursor(after), PageRequest.of(0, limit)));
    }

    @Override
    public List<OpenMatchMessage> findDirect(UUID openMatchId, UUID userA, UUID userB, Instant after, int limit) {
        return chronological(jpa.findDirectNewestFirst(openMatchId, userA, userB, cursor(after),
                PageRequest.of(0, limit)));
    }

    @Override
    public Optional<OpenMatchMessage> findLastTable(UUID openMatchId) {
        return jpa.findTableNewestFirst(openMatchId, BEGINNING, PageRequest.of(0, 1)).stream()
                .findFirst()
                .map(this::toDomain);
    }

    @Override
    public Optional<OpenMatchMessage> findLastDirect(UUID openMatchId, UUID userA, UUID userB) {
        return jpa.findDirectNewestFirst(openMatchId, userA, userB, BEGINNING, PageRequest.of(0, 1)).stream()
                .findFirst()
                .map(this::toDomain);
    }

    private List<OpenMatchMessage> chronological(List<OpenMatchMessageJpaEntity> newestFirst) {
        List<OpenMatchMessage> messages = new ArrayList<>(newestFirst.stream().map(this::toDomain).toList());
        Collections.reverse(messages);
        return messages;
    }

    private OpenMatchMessage toDomain(OpenMatchMessageJpaEntity e) {
        return new OpenMatchMessage(e.getId(), e.getOpenMatchId(), e.getSenderId(), e.getRecipientId(), e.getBody(),
                instant(e.getCreatedAt()), e.getCreatedBy(), instant(e.getUpdatedAt()), e.getUpdatedBy());
    }

    private static LocalDateTime cursor(Instant after) {
        return after == null ? BEGINNING : utc(after);
    }

    private static LocalDateTime utc(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static Instant instant(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.toInstant(ZoneOffset.UTC);
    }
}

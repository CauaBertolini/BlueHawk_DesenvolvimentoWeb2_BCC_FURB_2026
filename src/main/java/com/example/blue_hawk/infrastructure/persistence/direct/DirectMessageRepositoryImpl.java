package com.example.blue_hawk.infrastructure.persistence.direct;

import com.example.blue_hawk.domain.entity.direct.DirectMessage;
import com.example.blue_hawk.domain.repository.IDirectMessageRepository;
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
public class DirectMessageRepositoryImpl implements IDirectMessageRepository {

    /** Sem {@code after}: qualquer data serve */
    private static final LocalDateTime BEGINNING = LocalDateTime.of(1970, 1, 1, 0, 0);

    private final DirectMessageJpaRepository jpa;

    public DirectMessageRepositoryImpl(DirectMessageJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public DirectMessage save(DirectMessage m) {
        return toDomain(jpa.save(new DirectMessageJpaEntity(m.getId(), m.getConversationId(), m.getSenderId(),
                m.getBody(), utc(m.getCreatedAt()), m.getCreatedBy(), utc(m.getUpdatedAt()), m.getUpdatedBy())));
    }

    @Override
    public List<DirectMessage> find(UUID conversationId, Instant after, int limit) {
        LocalDateTime cursor = after == null ? BEGINNING : utc(after);
        List<DirectMessage> messages = new ArrayList<>(jpa.findNewestFirst(conversationId, cursor,
                PageRequest.of(0, limit)).stream().map(DirectMessageRepositoryImpl::toDomain).toList());
        Collections.reverse(messages);
        return messages;
    }

    @Override
    public Optional<DirectMessage> findLast(UUID conversationId) {
        return jpa.findNewestFirst(conversationId, BEGINNING, PageRequest.of(0, 1)).stream()
                .findFirst()
                .map(DirectMessageRepositoryImpl::toDomain);
    }

    private static DirectMessage toDomain(DirectMessageJpaEntity e) {
        return new DirectMessage(e.getId(), e.getConversationId(), e.getSenderId(), e.getBody(),
                instant(e.getCreatedAt()), e.getCreatedBy(), instant(e.getUpdatedAt()), e.getUpdatedBy());
    }

    private static LocalDateTime utc(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static Instant instant(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.toInstant(ZoneOffset.UTC);
    }
}

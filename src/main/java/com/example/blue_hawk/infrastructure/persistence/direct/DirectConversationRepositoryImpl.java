package com.example.blue_hawk.infrastructure.persistence.direct;

import com.example.blue_hawk.domain.entity.direct.DirectConversation;
import com.example.blue_hawk.domain.repository.IDirectConversationRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Datas gravadas em UTC (colunas TIMESTAMP sem fuso). */
@Repository
public class DirectConversationRepositoryImpl implements IDirectConversationRepository {

    private final DirectConversationJpaRepository jpa;

    public DirectConversationRepositoryImpl(DirectConversationJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public DirectConversation save(DirectConversation c) {
        // saveAndFlush: a UNIQUE do par estoura aqui (dentro do caso de uso) se duas criações correrem juntas
        return toDomain(jpa.saveAndFlush(new DirectConversationJpaEntity(c.getId(), c.getUserAId(), c.getUserBId(),
                utc(c.getLastMessageAt()), utc(c.getCreatedAt()), c.getCreatedBy(), utc(c.getUpdatedAt()),
                c.getUpdatedBy())));
    }

    @Override
    public Optional<DirectConversation> findById(UUID id) {
        return jpa.findById(id).map(DirectConversationRepositoryImpl::toDomain);
    }

    @Override
    public Optional<DirectConversation> findBetween(UUID userA, UUID userB) {
        UUID[] pair = DirectConversation.orderedPair(userA, userB);
        return jpa.findByUserAIdAndUserBId(pair[0], pair[1]).map(DirectConversationRepositoryImpl::toDomain);
    }

    @Override
    public List<DirectConversation> findInvolving(UUID userId) {
        return jpa.findInvolving(userId).stream().map(DirectConversationRepositoryImpl::toDomain).toList();
    }

    private static DirectConversation toDomain(DirectConversationJpaEntity e) {
        return new DirectConversation(e.getId(), e.getUserAId(), e.getUserBId(), instant(e.getLastMessageAt()),
                instant(e.getCreatedAt()), e.getCreatedBy(), instant(e.getUpdatedAt()), e.getUpdatedBy());
    }

    private static LocalDateTime utc(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static Instant instant(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.toInstant(ZoneOffset.UTC);
    }
}

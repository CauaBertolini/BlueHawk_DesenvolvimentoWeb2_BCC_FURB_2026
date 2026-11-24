package com.example.blue_hawk.infrastructure.persistence.notification;

import com.example.blue_hawk.domain.entity.notification.Notification;
import com.example.blue_hawk.domain.repository.INotificationRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Datas gravadas em UTC (colunas TIMESTAMP sem fuso). */
@Repository
public class NotificationRepositoryImpl implements INotificationRepository {

    /** Tipos de mensagem de conversa (chat das partidas, negociações da Loja e mensagens diretas) */
    private static final List<Notification.Type> CHAT_TYPES =
            List.of(Notification.Type.MATCH_MESSAGE, Notification.Type.OFFER_MESSAGE,
                    Notification.Type.DIRECT_MESSAGE);

    private final NotificationJpaRepository jpa;

    public NotificationRepositoryImpl(NotificationJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Notification save(Notification n) {
        return toDomain(jpa.save(new NotificationJpaEntity(n.getId(), n.getUserId(), n.getType(), n.getActorId(),
                n.getOpenMatchId(), n.getBoardgameId(), n.getThreadUserId(), n.getNegotiationId(),
                n.getDirectConversationId(), n.getCount(), utc(n.getReadAt()),
                utc(n.getCreatedAt()), n.getCreatedBy(), utc(n.getUpdatedAt()), n.getUpdatedBy())));
    }

    @Override
    public Optional<Notification> findById(UUID id) {
        return jpa.findById(id).map(this::toDomain);
    }

    @Override
    public List<Notification> findByUser(UUID userId, int page, int size) {
        return jpa.findByUser(userId, PageRequest.of(page, size)).stream().map(this::toDomain).toList();
    }

    @Override
    public long countUnread(UUID userId) {
        return jpa.countByUserIdAndReadAtIsNull(userId);
    }

    @Override
    public Optional<Notification> findUnreadMessage(UUID userId, UUID openMatchId, UUID threadUserId) {
        Optional<NotificationJpaEntity> row = threadUserId == null
                ? jpa.findFirstByUserIdAndTypeAndOpenMatchIdAndThreadUserIdIsNullAndReadAtIsNull(
                        userId, Notification.Type.MATCH_MESSAGE, openMatchId)
                : jpa.findFirstByUserIdAndTypeAndOpenMatchIdAndThreadUserIdAndReadAtIsNull(
                        userId, Notification.Type.MATCH_MESSAGE, openMatchId, threadUserId);
        return row.map(this::toDomain);
    }

    @Override
    public List<Notification> findUnreadMessages(UUID userId, UUID openMatchId) {
        return jpa.findByUserIdAndTypeAndOpenMatchIdAndReadAtIsNull(userId, Notification.Type.MATCH_MESSAGE,
                openMatchId).stream().map(this::toDomain).toList();
    }

    @Override
    public int markMessagesRead(UUID userId, UUID openMatchId, UUID threadUserId, Instant readAt) {
        return threadUserId == null
                ? jpa.markTableRead(userId, Notification.Type.MATCH_MESSAGE, openMatchId, utc(readAt))
                : jpa.markDirectRead(userId, Notification.Type.MATCH_MESSAGE, openMatchId, threadUserId, utc(readAt));
    }

    @Override
    public Optional<Notification> findUnreadNegotiationMessage(UUID userId, UUID negotiationId) {
        return jpa.findFirstByUserIdAndTypeAndNegotiationIdAndReadAtIsNull(userId, Notification.Type.OFFER_MESSAGE,
                negotiationId).map(this::toDomain);
    }

    @Override
    public int markNegotiationMessagesRead(UUID userId, UUID negotiationId, Instant readAt) {
        return jpa.markNegotiationRead(userId, Notification.Type.OFFER_MESSAGE, negotiationId, utc(readAt));
    }

    @Override
    public Optional<Notification> findUnreadDirectMessage(UUID userId, UUID directConversationId) {
        return jpa.findFirstByUserIdAndTypeAndDirectConversationIdAndReadAtIsNull(userId,
                Notification.Type.DIRECT_MESSAGE, directConversationId).map(this::toDomain);
    }

    @Override
    public int markDirectMessagesRead(UUID userId, UUID directConversationId, Instant readAt) {
        return jpa.markDirectConversationRead(userId, Notification.Type.DIRECT_MESSAGE, directConversationId,
                utc(readAt));
    }

    @Override
    public List<Notification> findUnreadChatMessages(UUID userId) {
        return jpa.findByUserIdAndTypeInAndReadAtIsNull(userId, CHAT_TYPES).stream().map(this::toDomain).toList();
    }

    @Override
    public long countUnreadChatMessages(UUID userId) {
        return jpa.countByUserIdAndTypeInAndReadAtIsNull(userId, CHAT_TYPES);
    }

    @Override
    public int markAllRead(UUID userId, Instant readAt) {
        return jpa.markAllRead(userId, utc(readAt));
    }

    private Notification toDomain(NotificationJpaEntity e) {
        return new Notification(e.getId(), e.getUserId(), e.getType(), e.getActorId(), e.getOpenMatchId(),
                e.getBoardgameId(), e.getThreadUserId(), e.getNegotiationId(), e.getDirectConversationId(),
                e.getMessageCount(),
                instant(e.getReadAt()),
                instant(e.getCreatedAt()), e.getCreatedBy(), instant(e.getUpdatedAt()), e.getUpdatedBy());
    }

    private static LocalDateTime utc(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static Instant instant(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.toInstant(ZoneOffset.UTC);
    }
}

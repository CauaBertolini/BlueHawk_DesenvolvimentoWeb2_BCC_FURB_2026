package com.example.blue_hawk.infrastructure.persistence.notification;

import com.example.blue_hawk.domain.entity.notification.Notification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationJpaRepository extends JpaRepository<NotificationJpaEntity, UUID> {

    /** Da última atividade (updated_at, ou created_at se nunca agregou) para a mais antiga */
    @Query("""
            select n from NotificationJpaEntity n
            where n.userId = :userId
            order by coalesce(n.updatedAt, n.createdAt) desc, n.id desc
            """)
    List<NotificationJpaEntity> findByUser(@Param("userId") UUID userId, Pageable pageable);

    long countByUserIdAndReadAtIsNull(UUID userId);

    Optional<NotificationJpaEntity> findFirstByUserIdAndTypeAndOpenMatchIdAndThreadUserIdIsNullAndReadAtIsNull(
            UUID userId, Notification.Type type, UUID openMatchId);

    Optional<NotificationJpaEntity> findFirstByUserIdAndTypeAndOpenMatchIdAndThreadUserIdAndReadAtIsNull(
            UUID userId, Notification.Type type, UUID openMatchId, UUID threadUserId);

    List<NotificationJpaEntity> findByUserIdAndTypeAndOpenMatchIdAndReadAtIsNull(
            UUID userId, Notification.Type type, UUID openMatchId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update NotificationJpaEntity n set n.readAt = :readAt
            where n.userId = :userId and n.type = :type and n.openMatchId = :openMatchId
              and n.threadUserId is null and n.readAt is null
            """)
    int markTableRead(@Param("userId") UUID userId, @Param("type") Notification.Type type,
                      @Param("openMatchId") UUID openMatchId, @Param("readAt") LocalDateTime readAt);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update NotificationJpaEntity n set n.readAt = :readAt
            where n.userId = :userId and n.type = :type and n.openMatchId = :openMatchId
              and n.threadUserId = :threadUserId and n.readAt is null
            """)
    int markDirectRead(@Param("userId") UUID userId, @Param("type") Notification.Type type,
                       @Param("openMatchId") UUID openMatchId, @Param("threadUserId") UUID threadUserId,
                       @Param("readAt") LocalDateTime readAt);

    Optional<NotificationJpaEntity> findFirstByUserIdAndTypeAndNegotiationIdAndReadAtIsNull(
            UUID userId, Notification.Type type, UUID negotiationId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update NotificationJpaEntity n set n.readAt = :readAt
            where n.userId = :userId and n.type = :type and n.negotiationId = :negotiationId and n.readAt is null
            """)
    int markNegotiationRead(@Param("userId") UUID userId, @Param("type") Notification.Type type,
                            @Param("negotiationId") UUID negotiationId, @Param("readAt") LocalDateTime readAt);

    List<NotificationJpaEntity> findByUserIdAndTypeInAndReadAtIsNull(UUID userId,
                                                                     Collection<Notification.Type> types);

    long countByUserIdAndTypeInAndReadAtIsNull(UUID userId, Collection<Notification.Type> types);

    Optional<NotificationJpaEntity> findFirstByUserIdAndTypeAndDirectConversationIdAndReadAtIsNull(
            UUID userId, Notification.Type type, UUID directConversationId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update NotificationJpaEntity n set n.readAt = :readAt
            where n.userId = :userId and n.type = :type and n.directConversationId = :conversationId
              and n.readAt is null
            """)
    int markDirectConversationRead(@Param("userId") UUID userId, @Param("type") Notification.Type type,
                                   @Param("conversationId") UUID conversationId,
                                   @Param("readAt") LocalDateTime readAt);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update NotificationJpaEntity n set n.readAt = :readAt where n.userId = :userId and n.readAt is null")
    int markAllRead(@Param("userId") UUID userId, @Param("readAt") LocalDateTime readAt);
}

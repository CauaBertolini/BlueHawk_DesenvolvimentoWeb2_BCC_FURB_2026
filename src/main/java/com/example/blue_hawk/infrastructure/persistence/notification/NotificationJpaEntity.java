package com.example.blue_hawk.infrastructure.persistence.notification;

import com.example.blue_hawk.domain.entity.notification.Notification;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;

import java.sql.Types;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "notification")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class NotificationJpaEntity {
    @Id
    @JdbcTypeCode(Types.CHAR)
    @Column(name = "id", nullable = false, updatable = false, length = 36)
    private UUID id;

    @JdbcTypeCode(Types.CHAR)
    @Column(name = "user_id", nullable = false, length = 36)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 40)
    private Notification.Type type;

    @JdbcTypeCode(Types.CHAR)
    @Column(name = "actor_id", length = 36)
    private UUID actorId;

    @JdbcTypeCode(Types.CHAR)
    @Column(name = "open_match_id", length = 36)
    private UUID openMatchId;

    @JdbcTypeCode(Types.CHAR)
    @Column(name = "board_game_id", length = 36)
    private UUID boardgameId;

    @JdbcTypeCode(Types.CHAR)
    @Column(name = "thread_user_id", length = 36)
    private UUID threadUserId;

    @JdbcTypeCode(Types.CHAR)
    @Column(name = "negotiation_id", length = 36)
    private UUID negotiationId;

    @JdbcTypeCode(Types.CHAR)
    @Column(name = "direct_conversation_id", length = 36)
    private UUID directConversationId;

    /** Mensagens agregadas (coluna count) */
    @Column(name = "count", nullable = false)
    private Integer messageCount;

    /** UTC */
    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @JdbcTypeCode(Types.CHAR)
    @Column(name = "created_by", length = 36)
    private UUID createdBy;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @JdbcTypeCode(Types.CHAR)
    @Column(name = "updated_by", length = 36)
    private UUID updatedBy;
}

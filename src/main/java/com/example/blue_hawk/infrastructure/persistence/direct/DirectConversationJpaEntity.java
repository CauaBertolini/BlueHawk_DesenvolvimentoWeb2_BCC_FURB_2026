package com.example.blue_hawk.infrastructure.persistence.direct;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "direct_conversation")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DirectConversationJpaEntity {
    @Id
    @JdbcTypeCode(Types.CHAR)
    @Column(name = "id", nullable = false, updatable = false, length = 36)
    private UUID id;

    @JdbcTypeCode(Types.CHAR)
    @Column(name = "user_a_id", nullable = false, updatable = false, length = 36)
    private UUID userAId;

    @JdbcTypeCode(Types.CHAR)
    @Column(name = "user_b_id", nullable = false, updatable = false, length = 36)
    private UUID userBId;

    /** UTC */
    @Column(name = "last_message_at")
    private LocalDateTime lastMessageAt;

    /** UTC */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @JdbcTypeCode(Types.CHAR)
    @Column(name = "created_by", length = 36, updatable = false)
    private UUID createdBy;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @JdbcTypeCode(Types.CHAR)
    @Column(name = "updated_by", length = 36)
    private UUID updatedBy;
}

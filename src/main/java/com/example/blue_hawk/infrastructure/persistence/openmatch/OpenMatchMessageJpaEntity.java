package com.example.blue_hawk.infrastructure.persistence.openmatch;

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
@Table(name = "open_match_message")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OpenMatchMessageJpaEntity {
    @Id
    @JdbcTypeCode(Types.CHAR)
    @Column(name = "id", nullable = false, updatable = false, length = 36)
    private UUID id;

    @JdbcTypeCode(Types.CHAR)
    @Column(name = "open_match_id", nullable = false, length = 36)
    private UUID openMatchId;

    @JdbcTypeCode(Types.CHAR)
    @Column(name = "sender_id", nullable = false, length = 36)
    private UUID senderId;

    /** null = mesa */
    @JdbcTypeCode(Types.CHAR)
    @Column(name = "recipient_id", length = 36)
    private UUID recipientId;

    @Column(name = "body", nullable = false, length = 1000)
    private String body;

    /** UTC */
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

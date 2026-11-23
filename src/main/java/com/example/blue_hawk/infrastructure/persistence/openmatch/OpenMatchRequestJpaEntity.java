package com.example.blue_hawk.infrastructure.persistence.openmatch;

import com.example.blue_hawk.domain.entity.openmatch.OpenMatchRequest;
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
@Table(name = "open_match_request")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OpenMatchRequestJpaEntity {
    @Id
    @JdbcTypeCode(Types.CHAR)
    @Column(name = "id", nullable = false, updatable = false, length = 36)
    private UUID id;

    @JdbcTypeCode(Types.CHAR)
    @Column(name = "open_match_id", nullable = false, length = 36)
    private UUID openMatchId;

    @JdbcTypeCode(Types.CHAR)
    @Column(name = "user_id", nullable = false, length = 36)
    private UUID userId;

    @Column(name = "message", nullable = false, length = 500)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OpenMatchRequest.Status status;

    @Column(name = "host_reply", length = 500)
    private String hostReply;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;
}

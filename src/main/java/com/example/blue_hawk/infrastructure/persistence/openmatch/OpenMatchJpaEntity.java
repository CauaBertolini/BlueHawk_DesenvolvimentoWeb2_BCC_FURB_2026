package com.example.blue_hawk.infrastructure.persistence.openmatch;

import com.example.blue_hawk.domain.entity.openmatch.OpenMatch;
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
@Table(name = "open_match")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OpenMatchJpaEntity {
    @Id
    @JdbcTypeCode(Types.CHAR)
    @Column(name = "id", nullable = false, updatable = false, length = 36)
    private UUID id;

    @JdbcTypeCode(Types.CHAR)
    @Column(name = "host_id", nullable = false, length = 36)
    private UUID hostId;

    @JdbcTypeCode(Types.CHAR)
    @Column(name = "user_board_game_id", nullable = false, length = 36)
    private UUID userBoardgameId;

    @Column(name = "city", nullable = false, length = 100)
    private String city;

    /** UTC */
    @Column(name = "scheduled_at", nullable = false)
    private LocalDateTime scheduledAt;

    @Column(name = "max_players", nullable = false)
    private Integer maxPlayers;

    @Column(name = "notes", length = 500)
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OpenMatch.Status status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}

package com.example.blue_hawk.infrastructure.persistence.boardgame;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;

import java.io.Serializable;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "board_game_like")
@IdClass(BoardgameLikeJpaEntity.Key.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BoardgameLikeJpaEntity {

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Key implements Serializable {
        private UUID boardgameId;
        private UUID userId;
    }

    @Id
    @JdbcTypeCode(Types.CHAR)
    @Column(name = "board_game_id", nullable = false, length = 36)
    private UUID boardgameId;

    @Id
    @JdbcTypeCode(Types.CHAR)
    @Column(name = "user_id", nullable = false, length = 36)
    private UUID userId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}

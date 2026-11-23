package com.example.blue_hawk.infrastructure.persistence.boardgame;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.sql.Types;
import java.time.LocalDateTime;
import java.util.UUID;

/** Foto de capa de um boardgame, separada para as listagens não carregarem os bytes. */
@Entity
@Table(name = "board_game_cover")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BoardgameCoverJpaEntity {
    @Id
    @JdbcTypeCode(Types.CHAR)
    @Column(name = "board_game_id", nullable = false, updatable = false, length = 36)
    private UUID boardgameId;

    @Column(name = "content_type", nullable = false, length = 50)
    private String contentType;

    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(name = "data", nullable = false)
    private byte[] data;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}

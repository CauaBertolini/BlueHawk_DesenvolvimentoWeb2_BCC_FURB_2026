package com.example.blue_hawk.infrastructure.persistence.boardgame;

import com.example.blue_hawk.domain.entity.boardgame.ApprovalStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import com.example.blue_hawk.infrastructure.persistence.category.CategoryJpaEntity;
import java.time.LocalDateTime;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Convert;
import jakarta.persistence.Converter;
import jakarta.persistence.AttributeConverter;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;

import java.sql.Types;
import java.util.UUID;
import java.time.LocalDate;
import java.sql.Date;

@Entity
@Table(name = "board_game")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BoardgameJpaEntity {
    @Id
    @JdbcTypeCode(Types.CHAR)
    @Column(name = "id", nullable = false, updatable = false, length = 36)
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", nullable = false)
    private String description;

    @Convert(converter = YearDateConverter.class)
    @Column(name = "release_date")
    private int releaseYear;

    @Column(name = "player_number", nullable = false)
    private Integer playerNumber;

    @JdbcTypeCode(Types.INTEGER)
    @Column(name = "gameplay_time")
    private int playTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "approval_status", nullable = false, length = 20)
    private ApprovalStatus approvalStatus;

    @JdbcTypeCode(Types.CHAR)
    @Column(name = "submitted_by", length = 36)
    private UUID submittedBy;

    @Column(name = "review_note", length = 500)
    private String reviewNote;

    /** EAGER: são poucas categorias e toda resposta de jogo traz o nome dela */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "category_id")
    private CategoryJpaEntity category;

    @Column(name = "cover_preset", length = 20)
    private String coverPreset;

    @Column(name = "cover_image_updated_at")
    private LocalDateTime coverImageUpdatedAt;

    /**
     * Só leitura para o JPA: muda apenas pelos incrementos atômicos do BoardgameJpaRepository, para salvar um
     * jogo (aprovar, editar) nunca sobrescrever o contador com um valor desatualizado.
     */
    @Column(name = "like_count", insertable = false, updatable = false)
    private Integer likeCount;

    @Converter
    public static class YearDateConverter implements AttributeConverter<Integer, Date> {
        @Override
        public Date convertToDatabaseColumn(Integer year) {
            if (year == null || year == 0) return Date.valueOf(LocalDate.now());
            return Date.valueOf(LocalDate.of(year, 1, 1));
        }

        @Override
        public Integer convertToEntityAttribute(Date dbData) {
            if (dbData == null) return null;
            return dbData.toLocalDate().getYear();
        }
    }
}

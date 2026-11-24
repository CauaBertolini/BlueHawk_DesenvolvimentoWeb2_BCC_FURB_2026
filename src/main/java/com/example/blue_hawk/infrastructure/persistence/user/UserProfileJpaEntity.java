package com.example.blue_hawk.infrastructure.persistence.user;

import com.example.blue_hawk.domain.entity.user.UserProfile;
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

/**
 * Perfil do usuário. A coluna {@code avatar_image} (bytes da foto) NÃO é mapeada aqui de propósito: ler o perfil
 * não carrega a imagem e salvar o perfil não a apaga. Ela é lida/gravada por consultas próprias do repositório.
 */
@Entity
@Table(name = "user_profile")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileJpaEntity {
    @Id
    @JdbcTypeCode(Types.CHAR)
    @Column(name = "user_id", nullable = false, updatable = false, length = 36)
    private UUID userId;

    @Column(name = "bio", length = 500)
    private String bio;

    @Column(name = "city", length = 100)
    private String city;

    @Enumerated(EnumType.STRING)
    @Column(name = "experience_level", length = 20)
    private UserProfile.ExperienceLevel experienceLevel;

    @Column(name = "favorite_genres", length = 200)
    private String favoriteGenres;

    @Column(name = "is_public", nullable = false)
    private boolean publicProfile;

    @Column(name = "show_shelf", nullable = false)
    private boolean showShelf;

    @Column(name = "match_alerts", nullable = false)
    private boolean matchAlerts;

    @Column(name = "offer_alerts", nullable = false)
    private boolean offerAlerts;

    @Column(name = "allow_direct_messages", nullable = false)
    private boolean allowDirectMessages;

    @Column(name = "avatar_preset", length = 40)
    private String avatarPreset;

    @Column(name = "avatar_content_type", length = 50)
    private String avatarContentType;

    /** UTC */
    @Column(name = "avatar_updated_at")
    private LocalDateTime avatarUpdatedAt;

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

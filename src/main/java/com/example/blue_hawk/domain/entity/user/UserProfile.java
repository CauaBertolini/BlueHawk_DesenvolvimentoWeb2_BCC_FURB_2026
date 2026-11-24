package com.example.blue_hawk.domain.entity.user;

import java.time.Instant;
import java.util.UUID;

/**
 * Perfil do usuário (tabela user_profile, 1:1 com "user"): extras, preferências e o avatar.
 * Quem não tem linha usa {@link #defaults(UUID)}: perfil público, estante visível, aceita mensagens
 * diretas, sem extras e sem avatar.
 *
 * <p>Os bytes da foto ficam fora da entidade (o repositório lê/grava à parte), para o perfil não carregar a
 * imagem. Aqui ficam só o tipo e o instante da foto, que viram a URL versionada.</p>
 */
public class UserProfile {

    /** Nível de experiência declarado pelo usuário */
    public enum ExperienceLevel { INICIANTE, INTERMEDIARIO, VETERANO }

    private final UUID userId;
    private String bio;
    private String city;
    private ExperienceLevel experienceLevel;
    private String favoriteGenres;
    private boolean isPublic;
    private boolean showShelf;
    private boolean matchAlerts;
    private boolean offerAlerts;
    /** Aceita mensagens diretas de outros usuários */
    private boolean allowDirectMessages;
    private String avatarPreset;
    private String avatarContentType;
    private Instant avatarUpdatedAt;
    private Instant createdAt;
    private UUID createdBy;
    private Instant updatedAt;
    private UUID updatedBy;
    /** false = ainda não existe no banco (perfil padrão em memória) */
    private boolean persisted;

    public UserProfile(UUID userId, String bio, String city, ExperienceLevel experienceLevel, String favoriteGenres,
                       boolean isPublic, boolean showShelf, boolean matchAlerts, boolean offerAlerts,
                       boolean allowDirectMessages, String avatarPreset, String avatarContentType,
                       Instant avatarUpdatedAt, Instant createdAt, UUID createdBy, Instant updatedAt, UUID updatedBy) {
        this.userId = userId;
        this.bio = bio;
        this.city = city;
        this.experienceLevel = experienceLevel;
        this.favoriteGenres = favoriteGenres;
        this.isPublic = isPublic;
        this.showShelf = showShelf;
        this.matchAlerts = matchAlerts;
        this.offerAlerts = offerAlerts;
        this.allowDirectMessages = allowDirectMessages;
        this.avatarPreset = avatarPreset;
        this.avatarContentType = avatarContentType;
        this.avatarUpdatedAt = avatarUpdatedAt;
        this.createdAt = createdAt;
        this.createdBy = createdBy;
        this.updatedAt = updatedAt;
        this.updatedBy = updatedBy;
        this.persisted = true;
    }

    /** Perfil padrão de quem ainda não tem linha (mesmos DEFAULTs da migration) */
    public static UserProfile defaults(UUID userId) {
        UserProfile profile = new UserProfile(userId, null, null, null, null, true, true, true, false, true,
                null, null, null, null, null, null, null);
        profile.persisted = false;
        return profile;
    }

    public boolean hasAvatarImage() {
        return avatarUpdatedAt != null;
    }

    /** Escolhe uma arte do site: a foto enviada (se houver) deixa de valer */
    public void choosePreset(String preset) {
        this.avatarPreset = preset;
        if (preset != null) {
            this.avatarContentType = null;
            this.avatarUpdatedAt = null;
        }
    }

    /** Foto enviada: a arte escolhida deixa de valer */
    public void useImage(String contentType, Instant at) {
        this.avatarContentType = contentType;
        this.avatarUpdatedAt = at;
        this.avatarPreset = null;
    }

    /** Sem foto e sem arte: o front mostra as iniciais */
    public void clearAvatar() {
        this.avatarPreset = null;
        this.avatarContentType = null;
        this.avatarUpdatedAt = null;
    }

    /** Auditoria: quem criou (na primeira gravação) e quem alterou por último */
    public void touch(UUID by, Instant now) {
        if (!persisted) {
            this.createdAt = now;
            this.createdBy = by;
        }
        this.updatedAt = now;
        this.updatedBy = by;
    }

    public UUID getUserId() { return userId; }

    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public ExperienceLevel getExperienceLevel() { return experienceLevel; }
    public void setExperienceLevel(ExperienceLevel experienceLevel) { this.experienceLevel = experienceLevel; }

    public String getFavoriteGenres() { return favoriteGenres; }
    public void setFavoriteGenres(String favoriteGenres) { this.favoriteGenres = favoriteGenres; }

    public boolean isPublic() { return isPublic; }
    public void setPublic(boolean isPublic) { this.isPublic = isPublic; }

    public boolean isShowShelf() { return showShelf; }
    public void setShowShelf(boolean showShelf) { this.showShelf = showShelf; }

    public boolean isMatchAlerts() { return matchAlerts; }
    public void setMatchAlerts(boolean matchAlerts) { this.matchAlerts = matchAlerts; }

    public boolean isOfferAlerts() { return offerAlerts; }
    public void setOfferAlerts(boolean offerAlerts) { this.offerAlerts = offerAlerts; }

    public boolean isAllowDirectMessages() { return allowDirectMessages; }
    public void setAllowDirectMessages(boolean allowDirectMessages) { this.allowDirectMessages = allowDirectMessages; }

    public String getAvatarPreset() { return avatarPreset; }

    public String getAvatarContentType() { return avatarContentType; }

    public Instant getAvatarUpdatedAt() { return avatarUpdatedAt; }

    public Instant getCreatedAt() { return createdAt; }

    public UUID getCreatedBy() { return createdBy; }

    public Instant getUpdatedAt() { return updatedAt; }

    public UUID getUpdatedBy() { return updatedBy; }

    public boolean isPersisted() { return persisted; }
}

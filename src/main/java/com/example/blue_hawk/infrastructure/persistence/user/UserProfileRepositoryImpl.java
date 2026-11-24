package com.example.blue_hawk.infrastructure.persistence.user;

import com.example.blue_hawk.domain.entity.user.UserProfile;
import com.example.blue_hawk.domain.repository.IUserProfileRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

@Repository
public class UserProfileRepositoryImpl implements IUserProfileRepository {

    private final UserProfileJpaRepository jpa;

    public UserProfileRepositoryImpl(UserProfileJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<UserProfile> findByUserId(UUID userId) {
        return jpa.findById(userId).map(UserProfileRepositoryImpl::toDomain);
    }

    @Override
    public UserProfile save(UserProfile p) {
        // saveAndFlush: as consultas nativas do avatar rodam logo depois e precisam da linha no banco
        return toDomain(jpa.saveAndFlush(new UserProfileJpaEntity(
                p.getUserId(), p.getBio(), p.getCity(), p.getExperienceLevel(), p.getFavoriteGenres(),
                p.isPublic(), p.isShowShelf(), p.isMatchAlerts(), p.isOfferAlerts(), p.isAllowDirectMessages(),
                p.getAvatarPreset(), p.getAvatarContentType(), utc(p.getAvatarUpdatedAt()),
                p.getCreatedAt() != null ? utc(p.getCreatedAt()) : LocalDateTime.now(ZoneOffset.UTC),
                p.getCreatedBy(), utc(p.getUpdatedAt()), p.getUpdatedBy())));
    }

    @Override
    public void saveAvatarImage(UUID userId, byte[] data) {
        if (data == null) {
            jpa.clearAvatarImage(userId.toString());
        } else {
            jpa.updateAvatarImage(userId.toString(), data);
        }
    }

    @Override
    public Optional<AvatarImage> findAvatarImage(UUID userId) {
        UserProfileJpaEntity profile = jpa.findById(userId).orElse(null);
        if (profile == null || profile.getAvatarUpdatedAt() == null || profile.getAvatarContentType() == null) {
            return Optional.empty();
        }
        return jpa.findAvatarImage(userId.toString()).stream().findFirst()
                .map(data -> new AvatarImage(profile.getAvatarContentType(), data,
                        profile.getAvatarUpdatedAt().toInstant(ZoneOffset.UTC)));
    }

    private static UserProfile toDomain(UserProfileJpaEntity e) {
        return new UserProfile(e.getUserId(), e.getBio(), e.getCity(), e.getExperienceLevel(), e.getFavoriteGenres(),
                e.isPublicProfile(), e.isShowShelf(), e.isMatchAlerts(), e.isOfferAlerts(), e.isAllowDirectMessages(),
                e.getAvatarPreset(), e.getAvatarContentType(), instant(e.getAvatarUpdatedAt()),
                instant(e.getCreatedAt()), e.getCreatedBy(), instant(e.getUpdatedAt()), e.getUpdatedBy());
    }

    private static LocalDateTime utc(Instant instant) {
        return instant != null ? LocalDateTime.ofInstant(instant, ZoneOffset.UTC) : null;
    }

    private static Instant instant(LocalDateTime dateTime) {
        return dateTime != null ? dateTime.toInstant(ZoneOffset.UTC) : null;
    }
}

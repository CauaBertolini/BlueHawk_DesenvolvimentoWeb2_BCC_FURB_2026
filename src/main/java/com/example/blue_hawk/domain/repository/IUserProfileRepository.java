package com.example.blue_hawk.domain.repository;

import com.example.blue_hawk.domain.entity.user.UserProfile;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Perfil do usuário (tabela user_profile). Os bytes do avatar são lidos/gravados à parte. */
public interface IUserProfileRepository {

    record AvatarImage(String contentType, byte[] data, Instant updatedAt) {
    }

    Optional<UserProfile> findByUserId(UUID userId);

    /** Grava os dados do perfil (não mexe nos bytes do avatar) */
    UserProfile save(UserProfile profile);

    /** Troca os bytes da foto; {@code null} apaga. O perfil precisa já existir (salve antes). */
    void saveAvatarImage(UUID userId, byte[] data);

    /** A foto enviada, se houver */
    Optional<AvatarImage> findAvatarImage(UUID userId);
}

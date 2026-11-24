package com.example.blue_hawk.infrastructure.persistence.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface UserProfileJpaRepository extends JpaRepository<UserProfileJpaEntity, UUID> {

    @Modifying
    @Query(value = "UPDATE user_profile SET avatar_image = :data WHERE user_id = :userId", nativeQuery = true)
    int updateAvatarImage(@Param("userId") String userId, @Param("data") byte[] data);

    @Modifying
    @Query(value = "UPDATE user_profile SET avatar_image = NULL WHERE user_id = :userId", nativeQuery = true)
    int clearAvatarImage(@Param("userId") String userId);

    /** Só os bytes da foto (vazia quando não há) */
    @Query(value = "SELECT avatar_image FROM user_profile WHERE user_id = :userId AND avatar_image IS NOT NULL",
            nativeQuery = true)
    List<byte[]> findAvatarImage(@Param("userId") String userId);
}

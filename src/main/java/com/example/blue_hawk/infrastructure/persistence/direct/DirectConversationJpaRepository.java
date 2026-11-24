package com.example.blue_hawk.infrastructure.persistence.direct;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DirectConversationJpaRepository extends JpaRepository<DirectConversationJpaEntity, UUID> {

    /** O par precisa vir ordenado (user_a_id &lt; user_b_id) */
    Optional<DirectConversationJpaEntity> findByUserAIdAndUserBId(UUID userAId, UUID userBId);

    @Query("select c from DirectConversationJpaEntity c where c.userAId = :userId or c.userBId = :userId")
    List<DirectConversationJpaEntity> findInvolving(@Param("userId") UUID userId);
}

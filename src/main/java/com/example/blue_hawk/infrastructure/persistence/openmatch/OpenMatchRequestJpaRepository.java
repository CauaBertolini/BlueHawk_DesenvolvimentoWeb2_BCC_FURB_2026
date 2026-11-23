package com.example.blue_hawk.infrastructure.persistence.openmatch;

import com.example.blue_hawk.domain.entity.openmatch.OpenMatchRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OpenMatchRequestJpaRepository extends JpaRepository<OpenMatchRequestJpaEntity, UUID> {

    Optional<OpenMatchRequestJpaEntity> findByOpenMatchIdAndUserId(UUID openMatchId, UUID userId);

    List<OpenMatchRequestJpaEntity> findByOpenMatchIdOrderByCreatedAtAsc(UUID openMatchId);

    long countByOpenMatchIdAndStatus(UUID openMatchId, OpenMatchRequest.Status status);
}

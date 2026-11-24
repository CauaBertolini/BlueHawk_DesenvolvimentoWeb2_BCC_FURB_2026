package com.example.blue_hawk.infrastructure.persistence.openmatch;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** As buscas devolvem da mais recente para a mais antiga (o limite pega as últimas); o RepositoryImpl inverte. */
@Repository
public interface OpenMatchMessageJpaRepository extends JpaRepository<OpenMatchMessageJpaEntity, UUID> {

    @Query("""
            select m from OpenMatchMessageJpaEntity m
            where m.openMatchId = :openMatchId and m.recipientId is null and m.createdAt > :after
            order by m.createdAt desc, m.id desc
            """)
    List<OpenMatchMessageJpaEntity> findTableNewestFirst(@Param("openMatchId") UUID openMatchId,
                                                         @Param("after") LocalDateTime after,
                                                         Pageable pageable);

    @Query("""
            select m from OpenMatchMessageJpaEntity m
            where m.openMatchId = :openMatchId and m.createdAt > :after
              and ((m.senderId = :userA and m.recipientId = :userB) or (m.senderId = :userB and m.recipientId = :userA))
            order by m.createdAt desc, m.id desc
            """)
    List<OpenMatchMessageJpaEntity> findDirectNewestFirst(@Param("openMatchId") UUID openMatchId,
                                                          @Param("userA") UUID userA,
                                                          @Param("userB") UUID userB,
                                                          @Param("after") LocalDateTime after,
                                                          Pageable pageable);
}

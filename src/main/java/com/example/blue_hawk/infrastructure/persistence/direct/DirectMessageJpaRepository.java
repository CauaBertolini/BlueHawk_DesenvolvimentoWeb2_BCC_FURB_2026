package com.example.blue_hawk.infrastructure.persistence.direct;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** A busca devolve da mais recente para a mais antiga (o limite pega as últimas); o RepositoryImpl inverte. */
@Repository
public interface DirectMessageJpaRepository extends JpaRepository<DirectMessageJpaEntity, UUID> {

    @Query("""
            select m from DirectMessageJpaEntity m
            where m.conversationId = :conversationId and m.createdAt > :after
            order by m.createdAt desc, m.id desc
            """)
    List<DirectMessageJpaEntity> findNewestFirst(@Param("conversationId") UUID conversationId,
                                                 @Param("after") LocalDateTime after,
                                                 Pageable pageable);
}

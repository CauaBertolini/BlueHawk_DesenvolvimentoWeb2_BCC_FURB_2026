package com.example.blue_hawk.infrastructure.persistence.openmatch;

import com.example.blue_hawk.domain.entity.openmatch.OpenMatch;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface OpenMatchJpaRepository extends JpaRepository<OpenMatchJpaEntity, UUID> {

    /** {@code cityPattern}: "%trecho%" já em minúsculas ("%%" = qualquer cidade) */
    @Query("""
            select m from OpenMatchJpaEntity m
            where m.status = :status and m.scheduledAt >= :from and lower(m.city) like :cityPattern
            order by m.scheduledAt asc
            """)
    List<OpenMatchJpaEntity> findUpcoming(@Param("status") OpenMatch.Status status,
                                          @Param("from") LocalDateTime from,
                                          @Param("cityPattern") String cityPattern,
                                          Pageable pageable);

    @Query("""
            select m from OpenMatchJpaEntity m
            where m.status = :status and m.scheduledAt >= :from and lower(m.city) like :cityPattern
              and m.userBoardgameId in (select c.id from UserBoardgameJpaEntity c where c.boardgameId = :boardgameId)
            order by m.scheduledAt asc
            """)
    List<OpenMatchJpaEntity> findUpcomingForBoardgame(@Param("status") OpenMatch.Status status,
                                                      @Param("from") LocalDateTime from,
                                                      @Param("cityPattern") String cityPattern,
                                                      @Param("boardgameId") UUID boardgameId,
                                                      Pageable pageable);

    /**
     * Partidas já realizadas pelo usuário, por jogo: data passada, não canceladas, e ele foi o host ou teve o
     * pedido aceito. Cada linha: [board_game_id, quantidade].
     */
    @Query(value = """
            SELECT ubg.board_game_id, COUNT(*)
            FROM open_match m
            JOIN user_board_game ubg ON ubg.id = m.user_board_game_id
            WHERE m.status = 'OPEN'
              AND m.scheduled_at < :now
              AND (m.host_id = :userId OR EXISTS (
                    SELECT 1 FROM open_match_request r
                    WHERE r.open_match_id = m.id AND r.user_id = :userId AND r.status = 'ACCEPTED'))
            GROUP BY ubg.board_game_id
            """, nativeQuery = true)
    List<Object[]> countPlayedByBoardgame(@Param("userId") String userId, @Param("now") LocalDateTime now);

    /** Inclui as canceladas: quem pediu precisa ver que o host cancelou */
    @Query("""
            select m from OpenMatchJpaEntity m
            where m.scheduledAt >= :from
              and (m.hostId = :userId or m.id in (
                    select r.openMatchId from OpenMatchRequestJpaEntity r
                    where r.userId = :userId and r.status in (
                        com.example.blue_hawk.domain.entity.openmatch.OpenMatchRequest.Status.PENDING,
                        com.example.blue_hawk.domain.entity.openmatch.OpenMatchRequest.Status.ACCEPTED)))
            order by m.scheduledAt asc
            """)
    List<OpenMatchJpaEntity> findUpcomingInvolving(@Param("userId") UUID userId, @Param("from") LocalDateTime from);

    /** Caixa de conversas: host ou com pedido em qualquer status (inclui canceladas) */
    @Query("""
            select m from OpenMatchJpaEntity m
            where m.scheduledAt >= :from
              and (m.hostId = :userId or m.id in (
                    select r.openMatchId from OpenMatchRequestJpaEntity r where r.userId = :userId))
            """)
    List<OpenMatchJpaEntity> findInvolvingSince(@Param("userId") UUID userId, @Param("from") LocalDateTime from);
}

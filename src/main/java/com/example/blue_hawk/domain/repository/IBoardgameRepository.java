package com.example.blue_hawk.domain.repository;

import com.example.blue_hawk.domain.entity.boardgame.ApprovalStatus;
import com.example.blue_hawk.domain.entity.boardgame.Boardgame;
import org.springframework.data.domain.Page;

import java.util.Optional;
import java.util.UUID;

public interface IBoardgameRepository {
    Boardgame save(Boardgame boardgame);

    Optional<Boardgame> findById(UUID boardgameId);

    /** Lista os jogos com o status de aprovação informado, filtrando opcionalmente pelo nome. */
    Page<Boardgame> findAll(String name, ApprovalStatus approvalStatus, int page, int size);

    /** Como {@link #findAll(String, ApprovalStatus, int, int)}; {@code mostLikedFirst} ordena pelo ranking de curtidas */
    Page<Boardgame> findAll(String name, ApprovalStatus approvalStatus, boolean mostLikedFirst, int page, int size);

    /** Como acima, só os jogos da categoria (nome exato, sem diferenciar maiúsculas) */
    Page<Boardgame> findAllInCategory(String name, String category, ApprovalStatus approvalStatus,
                                      boolean mostLikedFirst, int page, int size);

    void deleteById(String boardgameId);
}

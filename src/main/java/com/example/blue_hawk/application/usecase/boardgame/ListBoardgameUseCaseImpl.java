package com.example.blue_hawk.application.usecase.boardgame;

import com.example.blue_hawk.application.dto.auth.CurrentUser;
import com.example.blue_hawk.application.dto.boardgame.ListBoardgameOutput;
import com.example.blue_hawk.application.dto.boardgame.ListBoardgameQuery;
import com.example.blue_hawk.domain.entity.boardgame.ApprovalStatus;
import com.example.blue_hawk.domain.entity.boardgame.Boardgame;
import com.example.blue_hawk.domain.repository.IBoardgameLikeRepository;
import com.example.blue_hawk.domain.repository.IBoardgameRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class ListBoardgameUseCaseImpl implements ListBoardgameUseCase {

    private final IBoardgameRepository boardgameRepository;
    private final IBoardgameLikeRepository likeRepository;

    public ListBoardgameUseCaseImpl(IBoardgameRepository boardgameRepository, IBoardgameLikeRepository likeRepository) {
        this.boardgameRepository = boardgameRepository;
        this.likeRepository = likeRepository;
    }

    @Override
    public List<ListBoardgameOutput> handle(ListBoardgameQuery query) {
        String name = query.name() != null && !query.name().isBlank() ? query.name() : null;
        int page = query.page() != null ? query.page() : 0;
        int size = query.size() != null ? query.size() : 10;
        ApprovalStatus status = parseStatus(query.status());
        boolean mostLikedFirst = parseSort(query.sort());

        // A wiki (pública) só mostra o catálogo aprovado; a fila de aprovação é exclusiva do ADMIN
        CurrentUser currentUser = query.currentUser() != null ? query.currentUser() : CurrentUser.anonymous();
        if (status != ApprovalStatus.APPROVED && !currentUser.admin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only admins can list games pending review");
        }

        String category = query.category() != null && !query.category().isBlank() ? query.category() : null;
        List<Boardgame> games = (category == null
                ? boardgameRepository.findAll(name, status, mostLikedFirst, page, size)
                : boardgameRepository.findAllInCategory(name, category, status, mostLikedFirst, page, size))
                .getContent();

        // Uma consulta só para marcar o coração de toda a página
        if (currentUser.authenticated() && !games.isEmpty()) {
            Set<UUID> liked = likeRepository.findLikedAmong(currentUser.userId(),
                    games.stream().map(Boardgame::getId).toList());
            games.forEach(game -> game.setLikedByMe(liked.contains(game.getId())));
        }

        return games.stream().map(ListBoardgameOutput::from).toList();
    }

    private static ApprovalStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return ApprovalStatus.APPROVED;
        }
        try {
            return ApprovalStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid status: " + status);
        }
    }

    /** true = ranking de curtidas */
    private static boolean parseSort(String sort) {
        if (sort == null || sort.isBlank() || sort.equalsIgnoreCase("name")) return false;
        if (sort.equalsIgnoreCase("likes")) return true;
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sort must be name or likes");
    }
}

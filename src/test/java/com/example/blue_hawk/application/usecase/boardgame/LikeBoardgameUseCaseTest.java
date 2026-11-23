package com.example.blue_hawk.application.usecase.boardgame;

import com.example.blue_hawk.application.dto.auth.CurrentUser;
import com.example.blue_hawk.application.dto.boardgame.ListBoardgameOutput;
import com.example.blue_hawk.application.dto.boardgame.ListBoardgameQuery;
import com.example.blue_hawk.domain.entity.boardgame.ApprovalStatus;
import com.example.blue_hawk.domain.entity.boardgame.Boardgame;
import com.example.blue_hawk.domain.repository.IBoardgameLikeRepository;
import com.example.blue_hawk.domain.repository.IBoardgameRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LikeBoardgameUseCaseTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final CurrentUser USER = new CurrentUser(USER_ID, false);

    @Mock
    private IBoardgameRepository boardgameRepository;
    @Mock
    private IBoardgameLikeRepository likeRepository;

    private LikeBoardgameUseCaseImpl useCase() {
        return new LikeBoardgameUseCaseImpl(boardgameRepository, likeRepository);
    }

    @Test
    void likeReturnsUpdatedCount() {
        Boardgame catan = new Boardgame("Catan", "Classico", 1995, 4, 90);
        when(boardgameRepository.findById(catan.getId())).thenReturn(Optional.of(catan));
        when(likeRepository.countFor(catan.getId())).thenReturn(7);

        var output = useCase().like(catan.getId().toString(), USER);

        verify(likeRepository).like(catan.getId(), USER_ID);
        assertEquals(7, output.likeCount());
        assertTrue(output.likedByMe());
    }

    @Test
    void unlikeReturnsNotLiked() {
        Boardgame catan = new Boardgame("Catan", "Classico", 1995, 4, 90);
        when(boardgameRepository.findById(catan.getId())).thenReturn(Optional.of(catan));
        when(likeRepository.countFor(catan.getId())).thenReturn(6);

        var output = useCase().unlike(catan.getId().toString(), USER);

        verify(likeRepository).unlike(catan.getId(), USER_ID);
        assertFalse(output.likedByMe());
    }

    @Test
    void anonymousCannotLike() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> useCase().like(UUID.randomUUID().toString(), CurrentUser.anonymous()));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
    }

    @Test
    void pendingGameCannotBeLiked() {
        Boardgame pending = Boardgame.submittedBy(USER_ID, "Caseiro", "X", 2024, 2, 30);
        when(boardgameRepository.findById(pending.getId())).thenReturn(Optional.of(pending));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> useCase().like(pending.getId().toString(), USER));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        verify(likeRepository, never()).like(any(), any());
    }

    @Test
    void rankingSortsByLikesAndMarksWhatIliked() {
        Boardgame a = new Boardgame("A", "x", 2020, 2, 30);
        Boardgame b = new Boardgame("B", "x", 2020, 2, 30);
        a.setLikeCount(5);
        b.setLikeCount(2);
        when(boardgameRepository.findAll(null, ApprovalStatus.APPROVED, true, 0, 5))
                .thenReturn(new PageImpl<>(List.of(a, b)));
        when(likeRepository.findLikedAmong(USER_ID, List.of(a.getId(), b.getId()))).thenReturn(Set.of(b.getId()));

        List<ListBoardgameOutput> ranking = new ListBoardgameUseCaseImpl(boardgameRepository, likeRepository)
                .handle(new ListBoardgameQuery(null, null, "likes", 0, 5, USER));

        assertEquals(5, ranking.get(0).likeCount());
        assertFalse(ranking.get(0).likedByMe());
        assertTrue(ranking.get(1).likedByMe());
    }

    @Test
    void invalidSortIsRejected() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                new ListBoardgameUseCaseImpl(boardgameRepository, likeRepository)
                        .handle(new ListBoardgameQuery(null, null, "price", 0, 5, USER)));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }
}

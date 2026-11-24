package com.example.blue_hawk.application.usecase.boardgame;

import com.example.blue_hawk.application.usecase.notification.NotificationService;
import com.example.blue_hawk.domain.entity.notification.Notification;
import com.example.blue_hawk.application.dto.auth.CurrentUser;
import com.example.blue_hawk.application.dto.boardgame.CreateBoardgameCommand;
import com.example.blue_hawk.application.dto.boardgame.CreateBoardgameOutput;
import com.example.blue_hawk.application.dto.boardgame.GetBoardgameQuery;
import com.example.blue_hawk.application.dto.boardgame.ListBoardgameQuery;
import com.example.blue_hawk.application.dto.boardgame.ReviewBoardgameCommand;
import com.example.blue_hawk.application.dto.boardgame.UpdateBoardgameCommand;
import com.example.blue_hawk.domain.entity.boardgame.ApprovalStatus;
import com.example.blue_hawk.domain.entity.boardgame.Boardgame;
import com.example.blue_hawk.domain.entity.userboardgame.UserBoardgame;
import com.example.blue_hawk.domain.repository.IBoardgameRepository;
import com.example.blue_hawk.domain.repository.IUserBoardGameRepository;
import com.example.blue_hawk.domain.repository.ICategoryRepository;
import com.example.blue_hawk.domain.repository.IBoardgameLikeRepository;
import com.example.blue_hawk.domain.entity.category.Category;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BoardgameApprovalUseCaseTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID OTHER_ID = UUID.randomUUID();
    private static final CurrentUser USER = new CurrentUser(USER_ID, false);
    private static final CurrentUser OTHER = new CurrentUser(OTHER_ID, false);
    private static final CurrentUser ADMIN = new CurrentUser(UUID.randomUUID(), true);

    @Mock
    private IBoardgameRepository boardgameRepository;
    @Mock
    private IUserBoardGameRepository userBoardGameRepository;
    @Mock
    private ICategoryRepository categoryRepository;
    @Mock
    private IBoardgameLikeRepository likeRepository;
    @Mock
    private NotificationService notificationService;

    private static final UUID CATEGORY_ID = UUID.randomUUID();

    private CreateBoardgameCommand createCommand(CurrentUser currentUser) {
        return new CreateBoardgameCommand("Jogo Novo", "Um jogo cadastrado no teste", 2024, 4, 60,
                CATEGORY_ID.toString(), "waves-1", currentUser);
    }

    private void categoryExists() {
        when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(new Category(CATEGORY_ID, "Estratégia")));
    }

    @Test
    void userSubmissionIsPendingAndGoesToTheirShelf() {
        categoryExists();
        when(boardgameRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userBoardGameRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CreateBoardgameOutput output = new CreateBoardgameUseCaseImpl(boardgameRepository, userBoardGameRepository, categoryRepository)
                .handle(createCommand(USER));

        assertEquals("PENDING", output.approvalStatus());
        assertEquals(USER_ID.toString(), output.submittedBy());
        assertNotNull(output.userBoardgameId());
        verify(userBoardGameRepository).save(any(UserBoardgame.class));
    }

    @Test
    void adminCreationIsApprovedWithoutShelfCopy() {
        categoryExists();
        when(boardgameRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CreateBoardgameOutput output = new CreateBoardgameUseCaseImpl(boardgameRepository, userBoardGameRepository, categoryRepository)
                .handle(createCommand(ADMIN));

        assertEquals("APPROVED", output.approvalStatus());
        assertNull(output.userBoardgameId());
        verify(userBoardGameRepository, never()).save(any());
    }

    @Test
    void anonymousCannotCreate() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                new CreateBoardgameUseCaseImpl(boardgameRepository, userBoardGameRepository, categoryRepository)
                        .handle(createCommand(CurrentUser.anonymous())));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
    }

    @Test
    void wikiListsOnlyApprovedByDefault() {
        when(boardgameRepository.findAll(null, ApprovalStatus.APPROVED, false, 0, 10)).thenReturn(new PageImpl<>(List.of()));

        new ListBoardgameUseCaseImpl(boardgameRepository, likeRepository)
                .handle(new ListBoardgameQuery(null, null, null, 0, 10, CurrentUser.anonymous()));

        verify(boardgameRepository).findAll(null, ApprovalStatus.APPROVED, false, 0, 10);
    }

    @Test
    void wikiFiltersByCategory() {
        when(boardgameRepository.findAllInCategory(null, "Cartas", ApprovalStatus.APPROVED, false, 0, 10))
                .thenReturn(new PageImpl<>(List.of()));

        new ListBoardgameUseCaseImpl(boardgameRepository, likeRepository)
                .handle(new ListBoardgameQuery(null, null, null, 0, 10, CurrentUser.anonymous(), "Cartas"));

        verify(boardgameRepository).findAllInCategory(null, "Cartas", ApprovalStatus.APPROVED, false, 0, 10);
        verify(boardgameRepository, never()).findAll(any(), any(), anyBoolean(), anyInt(), anyInt());
    }

    @Test
    void onlyAdminSeesPendingQueue() {
        ListBoardgameUseCaseImpl useCase = new ListBoardgameUseCaseImpl(boardgameRepository, likeRepository);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> useCase.handle(new ListBoardgameQuery(null, "PENDING", null, 0, 10, USER)));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());

        when(boardgameRepository.findAll(null, ApprovalStatus.PENDING, false, 0, 10)).thenReturn(new PageImpl<>(List.of()));
        useCase.handle(new ListBoardgameQuery(null, "pending", null, 0, 10, ADMIN));
        verify(boardgameRepository).findAll(eq(null), eq(ApprovalStatus.PENDING), eq(false), eq(0), eq(10));
    }

    @Test
    void pendingGameIsHiddenFromOtherUsers() {
        Boardgame pending = Boardgame.submittedBy(USER_ID, "X", "Y", 2024, 2, 30);
        when(boardgameRepository.findById(pending.getId())).thenReturn(Optional.of(pending));
        GetBoardgameUseCaseImpl useCase = new GetBoardgameUseCaseImpl(boardgameRepository, likeRepository);

        assertEquals("PENDING", useCase.handle(new GetBoardgameQuery(pending.getId().toString(), USER)).approvalStatus());
        assertEquals("PENDING", useCase.handle(new GetBoardgameQuery(pending.getId().toString(), ADMIN)).approvalStatus());
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> useCase.handle(new GetBoardgameQuery(pending.getId().toString(), OTHER)));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void rejectKeepsReasonAndAuthorEditResubmits() {
        categoryExists();
        Boardgame pending = Boardgame.submittedBy(USER_ID, "X", "Y", 2024, 2, 30);
        when(boardgameRepository.findById(pending.getId())).thenReturn(Optional.of(pending));
        when(boardgameRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var rejected = new ReviewBoardgameUseCaseImpl(boardgameRepository, notificationService)
                .handle(new ReviewBoardgameCommand(pending.getId().toString(), false, "Nome duplicado", ADMIN));
        assertEquals("REJECTED", rejected.approvalStatus());
        assertEquals("Nome duplicado", rejected.reviewNote());
        // Quem enviou fica sabendo da recusa
        verify(notificationService).notifyBoardgame(USER_ID, Notification.Type.BOARDGAME_REJECTED, ADMIN.userId(),
                pending.getId());

        var edited = new UpdateBoardgameUseCaseImpl(boardgameRepository, categoryRepository).handle(new UpdateBoardgameCommand(
                pending.getId().toString(), "X corrigido", "Y", 2024, 2, 30, CATEGORY_ID.toString(), null, USER));
        assertEquals("PENDING", edited.approvalStatus());
    }

    @Test
    void userCannotEditApprovedGame() {
        Boardgame approved = new Boardgame("Catan", "Classico", 1995, 4, 90);
        when(boardgameRepository.findById(approved.getId())).thenReturn(Optional.of(approved));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                new UpdateBoardgameUseCaseImpl(boardgameRepository, categoryRepository).handle(new UpdateBoardgameCommand(
                        approved.getId().toString(), "Hack", "Y", 2024, 2, 30, CATEGORY_ID.toString(), null, USER)));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void createRequiresAnExistingCategory() {
        CreateBoardgameUseCaseImpl useCase =
                new CreateBoardgameUseCaseImpl(boardgameRepository, userBoardGameRepository, categoryRepository);

        ResponseStatusException missing = assertThrows(ResponseStatusException.class, () -> useCase.handle(
                new CreateBoardgameCommand("Jogo", "Desc", 2024, 4, 60, null, null, USER)));
        assertEquals(HttpStatus.BAD_REQUEST, missing.getStatusCode());

        UUID unknown = UUID.randomUUID();
        when(categoryRepository.findById(unknown)).thenReturn(Optional.empty());
        ResponseStatusException notFound = assertThrows(ResponseStatusException.class, () -> useCase.handle(
                new CreateBoardgameCommand("Jogo", "Desc", 2024, 4, 60, unknown.toString(), null, USER)));
        assertEquals(HttpStatus.BAD_REQUEST, notFound.getStatusCode());
        verify(boardgameRepository, never()).save(any());
    }

    @Test
    void createRejectsUnknownCoverPreset() {
        categoryExists();
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                new CreateBoardgameUseCaseImpl(boardgameRepository, userBoardGameRepository, categoryRepository)
                        .handle(new CreateBoardgameCommand("Jogo", "Desc", 2024, 4, 60,
                                CATEGORY_ID.toString(), "../../etc", USER)));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void createKeepsCategoryAndPreset() {
        categoryExists();
        when(boardgameRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userBoardGameRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CreateBoardgameOutput output = new CreateBoardgameUseCaseImpl(boardgameRepository, userBoardGameRepository,
                categoryRepository).handle(createCommand(USER));

        assertEquals(CATEGORY_ID.toString(), output.categoryId());
        assertEquals("waves-1", output.coverPreset());
        assertNull(output.coverImageVersion());
    }
}

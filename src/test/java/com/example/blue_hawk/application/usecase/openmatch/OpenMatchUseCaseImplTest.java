package com.example.blue_hawk.application.usecase.openmatch;

import com.example.blue_hawk.application.dto.auth.CurrentUser;
import com.example.blue_hawk.application.dto.openmatch.OpenMatchDtos.HostCommand;
import com.example.blue_hawk.application.dto.openmatch.OpenMatchDtos.OpenMatchView;
import com.example.blue_hawk.application.dto.realtime.RealtimeEvents.MatchChanged;
import com.example.blue_hawk.application.port.RealtimePublisher;
import com.example.blue_hawk.application.usecase.notification.NotificationService;
import com.example.blue_hawk.domain.entity.boardgame.Boardgame;
import com.example.blue_hawk.domain.entity.notification.Notification;
import com.example.blue_hawk.domain.entity.openmatch.OpenMatch;
import com.example.blue_hawk.domain.entity.openmatch.OpenMatchRequest;
import com.example.blue_hawk.domain.entity.userboardgame.UserBoardgame;
import com.example.blue_hawk.domain.repository.IBoardgameRepository;
import com.example.blue_hawk.domain.repository.IOpenMatchRepository;
import com.example.blue_hawk.domain.repository.IUserBoardGameRepository;
import com.example.blue_hawk.domain.repository.IUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OpenMatchUseCaseImplTest {

    private static final UUID HOST = UUID.randomUUID();
    private static final UUID GUEST = UUID.randomUUID();
    private static final CurrentUser HOST_USER = new CurrentUser(HOST, false);
    private static final CurrentUser GUEST_USER = new CurrentUser(GUEST, false);
    private static final String REASON = "Choveu muito, a estrada fechou";

    @Mock
    private IOpenMatchRepository openMatchRepository;
    @Mock
    private IUserBoardGameRepository userBoardGameRepository;
    @Mock
    private IBoardgameRepository boardgameRepository;
    @Mock
    private IUserRepository userRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private RealtimePublisher realtimePublisher;

    private OpenMatchUseCaseImpl useCase;
    private Boardgame game;
    private UserBoardgame hostCopy;

    @BeforeEach
    void setUp() {
        useCase = new OpenMatchUseCaseImpl(openMatchRepository, userBoardGameRepository, boardgameRepository, userRepository,
                notificationService, realtimePublisher);
        game = new Boardgame("Catan", "Classico", 1995, 4, 90);
        hostCopy = new UserBoardgame(HOST.toString(), game.getId().toString());
        when(userBoardGameRepository.findById(hostCopy.getId())).thenReturn(Optional.of(hostCopy));
        when(boardgameRepository.findById(game.getId())).thenReturn(Optional.of(game));
        when(userRepository.findById(any())).thenReturn(Optional.empty());
        when(openMatchRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(openMatchRepository.saveRequest(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private String tomorrow() {
        return Instant.now().plus(1, ChronoUnit.DAYS).toString();
    }

    private OpenMatch openMatch(int maxPlayers) {
        OpenMatch match = OpenMatch.host(HOST, hostCopy.getId(), "Blumenau", Instant.now().plus(1, ChronoUnit.DAYS),
                maxPlayers, null);
        when(openMatchRepository.findById(match.getId())).thenReturn(Optional.of(match));
        return match;
    }

    @Test
    void hostsWithOwnCopyUsingGameLimitByDefault() {
        OpenMatchView view = useCase.host(new HostCommand(hostCopy.getId().toString(), " Blumenau ", tomorrow(),
                null, "Trago petiscos", HOST_USER));

        assertEquals("Blumenau", view.city());
        assertEquals(4, view.maxPlayers());
        assertEquals(1, view.players(), "o host ocupa uma vaga");
        assertEquals(3, view.seatsLeft());
    }

    @Test
    void cannotHostWithSomeoneElsesCopy() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> useCase.host(
                new HostCommand(hostCopy.getId().toString(), "Blumenau", tomorrow(), null, null, GUEST_USER)));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void cannotHostPendingGameOrInThePast() {
        Boardgame pending = Boardgame.submittedBy(HOST, "Caseiro", "X", 2024, 4, 30);
        UserBoardgame copy = new UserBoardgame(HOST.toString(), pending.getId().toString());
        when(userBoardGameRepository.findById(copy.getId())).thenReturn(Optional.of(copy));
        when(boardgameRepository.findById(pending.getId())).thenReturn(Optional.of(pending));
        ResponseStatusException notApproved = assertThrows(ResponseStatusException.class, () -> useCase.host(
                new HostCommand(copy.getId().toString(), "Blumenau", tomorrow(), null, null, HOST_USER)));
        assertEquals(HttpStatus.CONFLICT, notApproved.getStatusCode());

        ResponseStatusException past = assertThrows(ResponseStatusException.class, () -> useCase.host(
                new HostCommand(hostCopy.getId().toString(), "Blumenau", "2020-01-01T10:00:00Z", null, null, HOST_USER)));
        assertEquals(HttpStatus.BAD_REQUEST, past.getStatusCode());
    }

    @Test
    void maxPlayersCannotExceedGameLimit() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> useCase.host(
                new HostCommand(hostCopy.getId().toString(), "Blumenau", tomorrow(), 6, null, HOST_USER)));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void guestRequestsWithMessage() {
        OpenMatch match = openMatch(4);
        when(openMatchRepository.findRequest(match.getId(), GUEST)).thenReturn(Optional.empty());

        useCase.requestToJoin(match.getId().toString(), "Posso levar a expansão?", GUEST_USER);

        ArgumentCaptor<OpenMatchRequest> saved = ArgumentCaptor.forClass(OpenMatchRequest.class);
        verify(openMatchRepository).saveRequest(saved.capture());
        assertEquals(OpenMatchRequest.Status.PENDING, saved.getValue().getStatus());
        assertEquals("Posso levar a expansão?", saved.getValue().getMessage());
    }

    @Test
    void requestNeedsMessageAndHostCannotRequest() {
        OpenMatch match = openMatch(4);
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> useCase.requestToJoin(match.getId().toString(), "  ", GUEST_USER)).getStatusCode());
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> useCase.requestToJoin(match.getId().toString(), "oi", HOST_USER)).getStatusCode());
    }

    @Test
    void fullMatchRejectsNewRequestsAndAcceptances() {
        OpenMatch match = openMatch(2);
        when(openMatchRepository.countAccepted(match.getId())).thenReturn(1L);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> useCase.requestToJoin(match.getId().toString(), "oi", GUEST_USER));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());

        OpenMatchRequest pending = OpenMatchRequest.create(match.getId(), GUEST, "oi");
        when(openMatchRepository.findRequestById(pending.getId())).thenReturn(Optional.of(pending));
        ResponseStatusException full = assertThrows(ResponseStatusException.class,
                () -> useCase.decide(pending.getId().toString(), true, null, HOST_USER));
        assertEquals(HttpStatus.CONFLICT, full.getStatusCode());
    }

    @Test
    void onlyHostDecidesAndDecisionKeepsReply() {
        OpenMatch match = openMatch(4);
        OpenMatchRequest pending = OpenMatchRequest.create(match.getId(), GUEST, "oi");
        when(openMatchRepository.findRequestById(pending.getId())).thenReturn(Optional.of(pending));

        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
                () -> useCase.decide(pending.getId().toString(), true, null, GUEST_USER)).getStatusCode());

        var decided = useCase.decide(pending.getId().toString(), true, "Te espero às 19h!", HOST_USER);
        assertEquals("ACCEPTED", decided.status());
        assertEquals("Te espero às 19h!", decided.hostReply());
    }

    @Test
    void rejectedGuestCannotRequestAgainButCancelledCan() {
        OpenMatch match = openMatch(4);
        OpenMatchRequest rejected = OpenMatchRequest.create(match.getId(), GUEST, "oi");
        rejected.reject(null);
        when(openMatchRepository.findRequest(match.getId(), GUEST)).thenReturn(Optional.of(rejected));
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> useCase.requestToJoin(match.getId().toString(), "de novo", GUEST_USER)).getStatusCode());

        OpenMatchRequest cancelled = OpenMatchRequest.create(match.getId(), GUEST, "oi");
        cancelled.cancel();
        when(openMatchRepository.findRequest(match.getId(), GUEST)).thenReturn(Optional.of(cancelled));
        useCase.requestToJoin(match.getId().toString(), "voltei", GUEST_USER);
        assertEquals(OpenMatchRequest.Status.PENDING, cancelled.getStatus());
        assertEquals("voltei", cancelled.getMessage());
    }

    @Test
    void playedStatsComeFromRealizedMatchesAndNeedLogin() {
        when(openMatchRepository.countPlayedByBoardgame(any(), any()))
                .thenReturn(java.util.Map.of(game.getId(), 3L));

        var played = useCase.playedByMe(GUEST_USER);
        assertEquals(1, played.size());
        assertEquals(game.getId().toString(), played.get(0).boardgameId());
        assertEquals(3L, played.get(0).plays());

        assertEquals(HttpStatus.UNAUTHORIZED, assertThrows(ResponseStatusException.class,
                () -> useCase.playedByMe(CurrentUser.anonymous())).getStatusCode());
    }

    @Test
    void onlyHostCancelsMatch() {
        OpenMatch match = openMatch(4);
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
                () -> useCase.cancel(match.getId().toString(), REASON, GUEST_USER)).getStatusCode());
        verify(openMatchRepository, never()).save(any());

        assertEquals("CANCELLED", useCase.cancel(match.getId().toString(), REASON, HOST_USER).status());
    }

    @Test
    void cancellingRequiresAReasonBetween10And300Characters() {
        OpenMatch match = openMatch(4);
        String id = match.getId().toString();

        for (String invalid : new String[]{null, "", "   ", "curto", "  nove 9!  ", "x".repeat(301)}) {
            assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                    () -> useCase.cancel(id, invalid, HOST_USER)).getStatusCode(), "reason: " + invalid);
        }
        verify(openMatchRepository, never()).save(any());
        assertEquals("OPEN", match.getStatus().name());
        assertNull(useCase.get(id, HOST_USER).cancelReason(), "aberta: sem motivo");
    }

    @Test
    void cancelReasonIsTrimmedSavedAndReturned() {
        OpenMatch match = openMatch(4);
        String id = match.getId().toString();

        OpenMatchView view = useCase.cancel(id, "  " + REASON + "  ", HOST_USER);

        assertEquals(REASON, view.cancelReason());
        ArgumentCaptor<OpenMatch> saved = ArgumentCaptor.forClass(OpenMatch.class);
        verify(openMatchRepository).save(saved.capture());
        assertEquals(REASON, saved.getValue().getCancelReason());
        assertEquals(OpenMatch.Status.CANCELLED, saved.getValue().getStatus());

        // Cancelar de novo não troca o motivo
        assertEquals(REASON, useCase.cancel(id, "Outro motivo qualquer", HOST_USER).cancelReason());
        assertEquals(300, useCase.cancel(openMatch(4).getId().toString(), "x".repeat(300), HOST_USER)
                .cancelReason().length(), "300 caracteres ainda vale");
    }

    // ── notificações ────────────────────────────────────────────────────────

    @Test
    void joinRequestNotifiesTheHost() {
        OpenMatch match = openMatch(4);
        when(openMatchRepository.findRequest(match.getId(), GUEST)).thenReturn(Optional.empty());

        useCase.requestToJoin(match.getId().toString(), "Posso levar petiscos?", GUEST_USER);

        verify(notificationService).notifyMatch(HOST, Notification.Type.JOIN_REQUEST_RECEIVED, GUEST, match.getId());
    }

    @Test
    void withdrawingNotifiesTheHost() {
        OpenMatch match = openMatch(4);
        OpenMatchRequest request = OpenMatchRequest.create(match.getId(), GUEST, "oi");
        when(openMatchRepository.findRequest(match.getId(), GUEST)).thenReturn(Optional.of(request));

        useCase.cancelMyRequest(match.getId().toString(), GUEST_USER);

        verify(notificationService).notifyMatch(HOST, Notification.Type.JOIN_REQUEST_WITHDRAWN, GUEST, match.getId());
    }

    @Test
    void decisionNotifiesTheRequester() {
        OpenMatch match = openMatch(4);
        OpenMatchRequest accepted = OpenMatchRequest.create(match.getId(), GUEST, "oi");
        OpenMatchRequest rejected = OpenMatchRequest.create(match.getId(), UUID.randomUUID(), "oi");
        when(openMatchRepository.findRequestById(accepted.getId())).thenReturn(Optional.of(accepted));
        when(openMatchRepository.findRequestById(rejected.getId())).thenReturn(Optional.of(rejected));

        useCase.decide(accepted.getId().toString(), true, "Bem-vindo", HOST_USER);
        useCase.decide(rejected.getId().toString(), false, null, HOST_USER);

        verify(notificationService).notifyMatch(GUEST, Notification.Type.JOIN_REQUEST_ACCEPTED, HOST, match.getId());
        verify(notificationService).notifyMatch(rejected.getUserId(), Notification.Type.JOIN_REQUEST_REJECTED, HOST,
                match.getId());
    }

    @Test
    void cancellingNotifiesOnlyActiveRequesters() {
        OpenMatch match = openMatch(4);
        OpenMatchRequest pending = OpenMatchRequest.create(match.getId(), GUEST, "oi");
        OpenMatchRequest gaveUp = OpenMatchRequest.create(match.getId(), UUID.randomUUID(), "oi");
        gaveUp.cancel();
        when(openMatchRepository.findRequests(match.getId())).thenReturn(java.util.List.of(pending, gaveUp));

        useCase.cancel(match.getId().toString(), REASON, HOST_USER);

        verify(notificationService).notifyMatch(GUEST, Notification.Type.MATCH_CANCELLED, HOST, match.getId());
        verify(notificationService, never()).notifyMatch(gaveUp.getUserId(), Notification.Type.MATCH_CANCELLED, HOST,
                match.getId());
        // Tempo real: host + todos com pedido (inclusive quem já desistiu)
        verify(realtimePublisher).publishToUsers(java.util.Set.of(HOST, GUEST, gaveUp.getUserId()),
                MatchChanged.of(match.getId().toString()));
    }

    @Test
    void stateChangesPublishMatchChanged() {
        OpenMatch match = openMatch(4);
        OpenMatchRequest pending = OpenMatchRequest.create(match.getId(), GUEST, "oi");
        when(openMatchRepository.findRequestById(pending.getId())).thenReturn(Optional.of(pending));
        when(openMatchRepository.findRequests(match.getId())).thenReturn(java.util.List.of(pending));
        MatchChanged event = MatchChanged.of(match.getId().toString());

        useCase.decide(pending.getId().toString(), true, null, HOST_USER);
        verify(realtimePublisher).publishToUsers(java.util.Set.of(HOST, GUEST), event);

        when(openMatchRepository.findRequest(match.getId(), GUEST)).thenReturn(Optional.of(pending));
        useCase.cancelMyRequest(match.getId().toString(), GUEST_USER);
        verify(realtimePublisher, org.mockito.Mockito.times(2)).publishToUsers(java.util.Set.of(HOST, GUEST), event);
    }
}

package com.example.blue_hawk.application.usecase.openmatch;

import com.example.blue_hawk.application.dto.auth.CurrentUser;
import com.example.blue_hawk.application.dto.openmatch.OpenMatchChatDtos.ChatsView;
import com.example.blue_hawk.application.dto.openmatch.OpenMatchChatDtos.MessageView;
import com.example.blue_hawk.application.usecase.notification.NotificationService;
import com.example.blue_hawk.domain.entity.notification.Notification;
import com.example.blue_hawk.domain.entity.openmatch.OpenMatch;
import com.example.blue_hawk.domain.entity.openmatch.OpenMatchMessage;
import com.example.blue_hawk.domain.entity.openmatch.OpenMatchRequest;
import com.example.blue_hawk.domain.repository.INotificationRepository;
import com.example.blue_hawk.domain.repository.IOpenMatchMessageRepository;
import com.example.blue_hawk.domain.repository.IOpenMatchRepository;
import com.example.blue_hawk.domain.repository.IUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OpenMatchChatUseCaseImplTest {

    private static final UUID HOST = UUID.randomUUID();
    private static final UUID PLAYER = UUID.randomUUID();
    private static final UUID PENDING = UUID.randomUUID();
    private static final UUID STRANGER = UUID.randomUUID();
    private static final UUID PLAYER2 = UUID.randomUUID();

    @Mock
    private IOpenMatchRepository openMatchRepository;
    @Mock
    private IOpenMatchMessageRepository messageRepository;
    @Mock
    private INotificationRepository notificationRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private IUserRepository userRepository;

    private OpenMatchChatUseCaseImpl useCase;
    private OpenMatch match;
    private OpenMatchRequest accepted;
    private OpenMatchRequest pending;

    private static CurrentUser as(UUID id) {
        return new CurrentUser(id, false);
    }

    @BeforeEach
    void setUp() {
        useCase = new OpenMatchChatUseCaseImpl(openMatchRepository, messageRepository, notificationRepository,
                notificationService, userRepository);
        match = OpenMatch.host(HOST, UUID.randomUUID(), "Blumenau", Instant.now().plus(1, ChronoUnit.DAYS), 4, null);
        accepted = OpenMatchRequest.create(match.getId(), PLAYER, "oi");
        accepted.accept(null);
        pending = OpenMatchRequest.create(match.getId(), PENDING, "posso?");

        when(openMatchRepository.findById(match.getId())).thenReturn(Optional.of(match));
        when(openMatchRepository.findRequest(match.getId(), PLAYER)).thenReturn(Optional.of(accepted));
        when(openMatchRepository.findRequest(match.getId(), PENDING)).thenReturn(Optional.of(pending));
        when(openMatchRepository.findRequest(match.getId(), STRANGER)).thenReturn(Optional.empty());
        when(openMatchRepository.findRequests(match.getId())).thenReturn(List.of(accepted, pending));
        when(userRepository.findById(any())).thenReturn(Optional.empty());
        when(messageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private String id() {
        return match.getId().toString();
    }

    private static HttpStatus statusOf(Runnable action) {
        return HttpStatus.valueOf(assertThrows(ResponseStatusException.class, action::run).getStatusCode().value());
    }

    // ── acesso ──────────────────────────────────────────────────────────────

    @Test
    void tableChatIsForHostAndAcceptedOnly() {
        useCase.send(id(), "Oi mesa", null, as(HOST));
        useCase.send(id(), "Chego às 20h", null, as(PLAYER));

        assertEquals(HttpStatus.FORBIDDEN, statusOf(() -> useCase.send(id(), "Oi", null, as(PENDING))));
        assertEquals(HttpStatus.FORBIDDEN, statusOf(() -> useCase.messages(id(), null, null, as(STRANGER))));
    }

    @Test
    void pendingRequesterCanTalkToHostButNotToOthers() {
        MessageView sent = useCase.send(id(), "Preciso levar algo?", HOST.toString(), as(PENDING));

        assertTrue(sent.mine());
        assertEquals(HOST.toString(), sent.recipientId());
        assertEquals(HttpStatus.FORBIDDEN,
                statusOf(() -> useCase.send(id(), "Oi", PLAYER.toString(), as(PENDING))));
    }

    @Test
    void hostCanOnlyTalkToPeopleWhoAskedToJoin() {
        useCase.send(id(), "Pode vir!", PENDING.toString(), as(HOST));

        assertEquals(HttpStatus.FORBIDDEN,
                statusOf(() -> useCase.send(id(), "Oi", STRANGER.toString(), as(HOST))));
    }

    @Test
    void closedRequestCanReadButNotWrite() {
        pending.cancel();

        useCase.messages(id(), HOST.toString(), null, as(PENDING));
        assertEquals(HttpStatus.FORBIDDEN,
                statusOf(() -> useCase.send(id(), "Ainda dá?", HOST.toString(), as(PENDING))));
    }

    @Test
    void cancelledMatchIsReadOnly() {
        match.cancel();

        useCase.messages(id(), null, null, as(HOST));
        assertEquals(HttpStatus.CONFLICT, statusOf(() -> useCase.send(id(), "Oi", null, as(HOST))));
    }

    @Test
    void validatesMessageBody() {
        assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> useCase.send(id(), "   ", null, as(HOST))));
        assertEquals(HttpStatus.BAD_REQUEST,
                statusOf(() -> useCase.send(id(), "x".repeat(1001), null, as(HOST))));
    }

    @Test
    void requiresLogin() {
        assertEquals(HttpStatus.UNAUTHORIZED,
                statusOf(() -> useCase.messages(id(), null, null, CurrentUser.anonymous())));
    }

    // ── notificações ────────────────────────────────────────────────────────

    @Test
    void tableMessageNotifiesEveryoneAtTheTableButTheSender() {
        useCase.send(id(), "Chego às 20h", null, as(PLAYER));

        verify(notificationService).notifyMessage(HOST, PLAYER, match.getId(), null);
        verify(notificationService, never()).notifyMessage(eq(PENDING), any(), any(), any());
        verify(notificationService, never()).notifyMessage(eq(PLAYER), any(), any(), any());
    }

    @Test
    void directMessageNotifiesTheRecipientWithTheSenderAsThread() {
        useCase.send(id(), "Pode vir!", PENDING.toString(), as(HOST));

        verify(notificationService).notifyMessage(PENDING, HOST, match.getId(), HOST);
    }

    @Test
    void readingAThreadMarksItsNotificationsAsRead() {
        when(messageRepository.findDirect(eq(match.getId()), eq(HOST), eq(PENDING), isNull(), anyInt()))
                .thenReturn(List.of());

        useCase.messages(id(), PENDING.toString(), null, as(HOST));
        useCase.messages(id(), null, null, as(HOST));

        verify(notificationService).markThreadRead(HOST, match.getId(), PENDING);
        verify(notificationService).markThreadRead(HOST, match.getId(), null);
    }

    // ── visão geral ─────────────────────────────────────────────────────────

    @Test
    void hostSeesTableMembersAndOneThreadPerRequester() {
        Notification unread = Notification.create(HOST, Notification.Type.MATCH_MESSAGE, PENDING, match.getId(), null,
                PENDING);
        unread.aggregate(PENDING);
        when(notificationRepository.findUnreadMessages(HOST, match.getId())).thenReturn(List.of(unread));

        ChatsView view = useCase.chats(id(), as(HOST));

        assertTrue(view.canUseTableChat());
        assertEquals(2, view.members().size(), "host + aceito");
        assertEquals(2, view.threads().size());
        assertEquals(2, view.threads().stream()
                .filter(t -> t.userId().equals(PENDING.toString())).findFirst().orElseThrow().unread());
        assertEquals(0, view.tableUnread());
    }

    @Test
    void pendingRequesterSeesOnlyTheHostThread() {
        ChatsView view = useCase.chats(id(), as(PENDING));

        assertFalse(view.canUseTableChat());
        assertTrue(view.members().isEmpty());
        assertEquals(1, view.threads().size());
        assertEquals("HOST", view.threads().get(0).requestStatus());
        assertEquals(HttpStatus.FORBIDDEN, statusOf(() -> useCase.chats(id(), as(STRANGER))));
    }

    // ── conversa direta entre jogadores aceitos ─────────────────────────────

    /** Um segundo jogador aceito na mesa */
    private OpenMatchRequest secondPlayer() {
        OpenMatchRequest second = OpenMatchRequest.create(match.getId(), PLAYER2, "eu também");
        second.accept(null);
        when(openMatchRepository.findRequest(match.getId(), PLAYER2)).thenReturn(Optional.of(second));
        when(openMatchRepository.findRequests(match.getId())).thenReturn(List.of(accepted, pending, second));
        return second;
    }

    private OpenMatchMessage directMessage(UUID sender, UUID recipient, Instant at) {
        return new OpenMatchMessage(UUID.randomUUID(), match.getId(), sender, recipient, "oi", at, sender, null,
                null);
    }

    @Test
    void acceptedPlayersCanTalkDirectly() {
        secondPlayer();

        MessageView sent = useCase.send(id(), "Levo o dado extra", PLAYER2.toString(), as(PLAYER));
        useCase.messages(id(), PLAYER.toString(), null, as(PLAYER2));

        assertEquals(PLAYER2.toString(), sent.recipientId());
        verify(notificationService).notifyMessage(PLAYER2, PLAYER, match.getId(), PLAYER);
        verify(notificationService).markThreadRead(PLAYER2, match.getId(), PLAYER);
    }

    @Test
    void pendingRequesterAndPlayerCannotTalkDirectly() {
        assertEquals(HttpStatus.FORBIDDEN,
                statusOf(() -> useCase.send(id(), "Oi", PENDING.toString(), as(PLAYER))));
        assertEquals(HttpStatus.FORBIDDEN,
                statusOf(() -> useCase.send(id(), "Oi", PLAYER.toString(), as(PENDING))));
        assertEquals(HttpStatus.FORBIDDEN,
                statusOf(() -> useCase.messages(id(), PENDING.toString(), null, as(PLAYER))));
        verify(messageRepository, never()).save(any());
    }

    @Test
    void nonMembersCannotTalkDirectlyWithPlayers() {
        assertEquals(HttpStatus.FORBIDDEN,
                statusOf(() -> useCase.send(id(), "Oi", PLAYER.toString(), as(STRANGER))));
        assertEquals(HttpStatus.FORBIDDEN,
                statusOf(() -> useCase.send(id(), "Oi", STRANGER.toString(), as(PLAYER))));
        assertEquals(HttpStatus.FORBIDDEN,
                statusOf(() -> useCase.messages(id(), PLAYER.toString(), null, as(STRANGER))));
    }

    @Test
    void playerWhoLeftKeepsTheHistoryReadableButCannotWrite() {
        OpenMatchRequest second = secondPlayer();
        when(messageRepository.findLastDirect(match.getId(), PLAYER, PLAYER2))
                .thenReturn(Optional.of(directMessage(PLAYER, PLAYER2, Instant.now())));
        when(messageRepository.findLastDirect(match.getId(), PLAYER2, PLAYER))
                .thenReturn(Optional.of(directMessage(PLAYER, PLAYER2, Instant.now())));
        second.cancel();

        useCase.messages(id(), PLAYER2.toString(), null, as(PLAYER));
        useCase.messages(id(), PLAYER.toString(), null, as(PLAYER2));
        assertEquals(HttpStatus.FORBIDDEN,
                statusOf(() -> useCase.send(id(), "Volta!", PLAYER2.toString(), as(PLAYER))));
        assertEquals(HttpStatus.FORBIDDEN,
                statusOf(() -> useCase.send(id(), "Saí", PLAYER.toString(), as(PLAYER2))));
    }

    @Test
    void cancelledMatchMakesPlayerConversationsReadOnly() {
        secondPlayer();
        match.cancel();

        useCase.messages(id(), PLAYER2.toString(), null, as(PLAYER));
        assertEquals(HttpStatus.CONFLICT,
                statusOf(() -> useCase.send(id(), "Oi", PLAYER2.toString(), as(PLAYER))));
    }

    @Test
    void acceptedPlayerSeesTheHostAndTheOtherAcceptedPlayers() {
        secondPlayer();
        when(messageRepository.findLastDirect(match.getId(), PLAYER, PLAYER2))
                .thenReturn(Optional.of(directMessage(PLAYER2, PLAYER, Instant.now())));
        Notification unread = Notification.create(PLAYER, Notification.Type.MATCH_MESSAGE, PLAYER2, match.getId(),
                null, PLAYER2);
        when(notificationRepository.findUnreadMessages(PLAYER, match.getId())).thenReturn(List.of(unread));

        ChatsView view = useCase.chats(id(), as(PLAYER));

        assertEquals(List.of(PLAYER2.toString(), HOST.toString()),
                view.threads().stream().map(t -> t.userId()).toList(), "com atividade primeiro; pendente fica de fora");
        assertEquals("ACCEPTED", view.threads().get(0).requestStatus());
        assertEquals(1, view.threads().get(0).unread());
        assertEquals("HOST", view.threads().get(1).requestStatus());
        assertEquals(3, view.members().size(), "host + 2 aceitos");
    }

    @Test
    void withoutActivityTheHostThreadComesFirst() {
        secondPlayer();

        ChatsView view = useCase.chats(id(), as(PLAYER2));

        assertEquals(List.of(HOST.toString(), PLAYER.toString()),
                view.threads().stream().map(t -> t.userId()).toList());
    }
}

package com.example.blue_hawk.application.usecase.direct;

import com.example.blue_hawk.application.port.RealtimePublisher;
import com.example.blue_hawk.application.dto.auth.CurrentUser;
import com.example.blue_hawk.application.dto.direct.DirectConversationDtos.ConversationView;
import com.example.blue_hawk.application.dto.direct.DirectConversationDtos.MessageView;
import com.example.blue_hawk.application.dto.realtime.RealtimeEvents.ChatMessage;
import com.example.blue_hawk.application.dto.realtime.RealtimeEvents.DirectChannel;
import com.example.blue_hawk.application.usecase.direct.DirectConversationUseCase.OpenResult;
import com.example.blue_hawk.application.usecase.notification.NotificationService;
import com.example.blue_hawk.domain.entity.direct.DirectConversation;
import com.example.blue_hawk.domain.entity.direct.DirectMessage;
import com.example.blue_hawk.domain.entity.notification.Notification;
import com.example.blue_hawk.domain.entity.user.User;
import com.example.blue_hawk.domain.entity.user.UserProfile;
import com.example.blue_hawk.domain.entity.user.UserRole;
import com.example.blue_hawk.domain.repository.IDirectConversationRepository;
import com.example.blue_hawk.domain.repository.IDirectMessageRepository;
import com.example.blue_hawk.domain.repository.INotificationRepository;
import com.example.blue_hawk.domain.repository.IUserProfileRepository;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
class DirectConversationUseCaseImplTest {

    @Mock
    private RealtimePublisher realtimePublisher;

    private static final UUID ME = UUID.randomUUID();
    private static final UUID ANA = UUID.randomUUID();
    private static final UUID BOB = UUID.randomUUID();

    @Mock
    private IDirectConversationRepository conversationRepository;
    @Mock
    private IDirectMessageRepository messageRepository;
    @Mock
    private IUserRepository userRepository;
    @Mock
    private IUserProfileRepository profileRepository;
    @Mock
    private INotificationRepository notificationRepository;
    @Mock
    private NotificationService notificationService;

    private DirectConversationUseCaseImpl useCase;
    /** "Banco" das conversas: o repositório falso procura o par em qualquer ordem */
    private final List<DirectConversation> stored = new ArrayList<>();

    private static CurrentUser as(UUID id) {
        return new CurrentUser(id, false);
    }

    @BeforeEach
    void setUp() {
        useCase = new DirectConversationUseCaseImpl(conversationRepository, messageRepository, userRepository,
                profileRepository, notificationRepository, notificationService, realtimePublisher);
        Map<UUID, String> names = Map.of(ME, "Eu", ANA, "Ana Souza", BOB, "Bob");
        when(userRepository.findById(any())).thenAnswer(inv -> {
            UUID id = inv.getArgument(0);
            return Optional.ofNullable(names.get(id)).map(n -> new User(id, n, n + "@x.com", "h", UserRole.USER));
        });
        when(profileRepository.findByUserId(any())).thenReturn(Optional.empty());
        when(conversationRepository.save(any())).thenAnswer(inv -> {
            DirectConversation c = inv.getArgument(0);
            stored.removeIf(s -> s.getId().equals(c.getId()));
            stored.add(c);
            return c;
        });
        when(conversationRepository.findBetween(any(), any())).thenAnswer(inv -> {
            UUID a = inv.getArgument(0);
            UUID b = inv.getArgument(1);
            return stored.stream().filter(c -> c.isParticipant(a) && c.isParticipant(b)).findFirst();
        });
        when(conversationRepository.findById(any())).thenAnswer(inv -> stored.stream()
                .filter(c -> c.getId().equals(inv.getArgument(0))).findFirst());
        when(messageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(notificationRepository.findUnreadDirectMessage(any(), any())).thenReturn(Optional.empty());
    }

    private static HttpStatus statusOf(Runnable action) {
        return HttpStatus.valueOf(assertThrows(ResponseStatusException.class, action::run).getStatusCode().value());
    }

    private void blocksDirectMessages(UUID userId) {
        UserProfile profile = UserProfile.defaults(userId);
        profile.setAllowDirectMessages(false);
        when(profileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
    }

    private String conversationWithAna() {
        return useCase.open(ANA.toString(), as(ME)).conversation().id();
    }

    // ── get-or-create ───────────────────────────────────────────────────────

    @Test
    void openIsIdempotentInEitherOrder() {
        OpenResult first = useCase.open(ANA.toString(), as(ME));
        OpenResult again = useCase.open(ANA.toString(), as(ME));
        OpenResult fromAna = useCase.open(ME.toString(), as(ANA));

        assertTrue(first.created());
        assertFalse(again.created());
        assertFalse(fromAna.created());
        assertEquals(first.conversation().id(), again.conversation().id());
        assertEquals(first.conversation().id(), fromAna.conversation().id());
        assertEquals(1, stored.size());

        DirectConversation saved = stored.get(0);
        assertTrue(saved.getUserAId().toString().compareTo(saved.getUserBId().toString()) < 0, "par ordenado");
        assertEquals(ME, saved.getCreatedBy());

        ConversationView view = first.conversation();
        assertEquals(ANA.toString(), view.otherUser().id());
        assertEquals("Ana Souza", view.otherUser().name());
        assertTrue(view.canSend());
        assertNull(view.lastMessage());
        assertNull(view.lastMessageAt());
        assertNotNull(view.createdAt());
        assertEquals(ME.toString(), fromAna.conversation().otherUser().id());
    }

    @Test
    void openRejectsSelfInvalidAndUnknownUsers() {
        assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> useCase.open(ME.toString(), as(ME))));
        assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> useCase.open("nope", as(ME))));
        assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> useCase.open(null, as(ME))));
        assertEquals(HttpStatus.NOT_FOUND, statusOf(() -> useCase.open(UUID.randomUUID().toString(), as(ME))));
        assertEquals(HttpStatus.UNAUTHORIZED,
                statusOf(() -> useCase.open(ANA.toString(), CurrentUser.anonymous())));
        verify(conversationRepository, never()).save(any());
    }

    @Test
    void blockedUserCannotBeContactedButExistingConversationStillOpens() {
        String id = conversationWithAna();
        blocksDirectMessages(ANA);
        blocksDirectMessages(BOB);

        assertEquals(HttpStatus.FORBIDDEN, statusOf(() -> useCase.open(BOB.toString(), as(ME))));

        OpenResult existing = useCase.open(ANA.toString(), as(ME));
        assertFalse(existing.created());
        assertEquals(id, existing.conversation().id());
        assertFalse(existing.conversation().canSend());
        assertEquals(HttpStatus.FORBIDDEN, statusOf(() -> useCase.send(id, "Oi", as(ME))));
        // A Ana bloqueou, mas ela mesma ainda escreve para mim (eu aceito)
        useCase.send(id, "Oi, tudo bem?", as(ANA));
    }

    // ── mensagens ───────────────────────────────────────────────────────────

    @Test
    void sendTrimsNotifiesAndUpdatesTheLastActivity() {
        String id = conversationWithAna();

        MessageView sent = useCase.send(id, "  Bora jogar Catan?  ", as(ME));

        assertEquals("Bora jogar Catan?", sent.body());
        assertTrue(sent.mine());
        assertEquals("Eu", sent.senderName());
        verify(notificationService).notifyDirectMessage(ANA, ME, UUID.fromString(id));
        DirectConversation conversation = stored.get(0);
        assertEquals(sent.createdAt(), conversation.getLastMessageAt().toString());
        assertEquals(ME, conversation.getUpdatedBy());
    }

    @Test
    void sendValidatesTheBody() {
        String id = conversationWithAna();

        assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> useCase.send(id, "   ", as(ME))));
        assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> useCase.send(id, null, as(ME))));
        assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> useCase.send(id, "x".repeat(1001), as(ME))));
        useCase.send(id, "x".repeat(1000), as(ME));
    }

    @Test
    void onlyParticipantsCanReadOrWrite() {
        String id = conversationWithAna();

        assertEquals(HttpStatus.FORBIDDEN, statusOf(() -> useCase.get(id, as(BOB))));
        assertEquals(HttpStatus.FORBIDDEN, statusOf(() -> useCase.messages(id, null, as(BOB))));
        assertEquals(HttpStatus.FORBIDDEN, statusOf(() -> useCase.send(id, "Oi", as(BOB))));
        assertEquals(HttpStatus.NOT_FOUND, statusOf(() -> useCase.get(UUID.randomUUID().toString(), as(ME))));
        assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> useCase.get("nope", as(ME))));
    }

    @Test
    void listingPassesTheCursorMarksAsReadAndFlagsMine() {
        String id = conversationWithAna();
        UUID conversationId = UUID.fromString(id);
        Instant after = Instant.parse("2026-11-24T10:00:00Z");
        when(messageRepository.find(eq(conversationId), eq(after), anyInt())).thenReturn(List.of(
                new DirectMessage(UUID.randomUUID(), conversationId, ANA, "Oi!", after.plusSeconds(1), ANA, null,
                        null),
                new DirectMessage(UUID.randomUUID(), conversationId, ME, "Olá", after.plusSeconds(2), ME, null,
                        null)));

        List<MessageView> messages = useCase.messages(id, "2026-11-24T10:00:00Z", as(ME));

        assertEquals(2, messages.size());
        assertFalse(messages.get(0).mine());
        assertEquals("Ana Souza", messages.get(0).senderName());
        assertTrue(messages.get(1).mine());
        verify(notificationService).markDirectConversationRead(ME, conversationId);

        useCase.messages(id, null, as(ME));
        verify(messageRepository).find(eq(conversationId), isNull(), anyInt());
        assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> useCase.messages(id, "ontem", as(ME))));
    }

    @Test
    void viewShowsUnreadAndLastMessage() {
        String id = conversationWithAna();
        UUID conversationId = UUID.fromString(id);
        Notification unread = Notification.createForDirect(ME, ANA, conversationId);
        unread.aggregate(ANA);
        when(notificationRepository.findUnreadDirectMessage(ME, conversationId)).thenReturn(Optional.of(unread));
        Instant at = Instant.parse("2026-11-24T11:00:00Z");
        when(messageRepository.findLast(conversationId)).thenReturn(Optional.of(
                new DirectMessage(UUID.randomUUID(), conversationId, ANA, "Tô chegando", at, ANA, null, null)));

        ConversationView view = useCase.get(id, as(ME));

        assertEquals(2, view.unread());
        assertEquals("Tô chegando", view.lastMessage().body());
        assertEquals(ANA.toString(), view.lastMessage().senderId());
        assertEquals(at.toString(), view.lastMessage().createdAt());
    }

    @Test
    void savesTheNotificationThroughTheService() {
        String id = conversationWithAna();

        useCase.send(id, "Oi", as(ANA));

        ArgumentCaptor<UUID> recipient = ArgumentCaptor.forClass(UUID.class);
        verify(notificationService).notifyDirectMessage(recipient.capture(), eq(ANA), eq(UUID.fromString(id)));
        assertEquals(ME, recipient.getValue());
    }

    @Test
    void sendIsPushedToBothParticipantsWithMinePerRecipient() {
        String id = conversationWithAna();

        useCase.send(id, "Oi", as(ME));

        ArgumentCaptor<Object> toMe = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<Object> toAna = ArgumentCaptor.forClass(Object.class);
        verify(realtimePublisher).publishToUser(eq(ME), toMe.capture());
        verify(realtimePublisher).publishToUser(eq(ANA), toAna.capture());
        ChatMessage mine = (ChatMessage) toMe.getValue();
        ChatMessage theirs = (ChatMessage) toAna.getValue();
        assertEquals("chat.message", mine.type());
        assertEquals(DirectChannel.of(id), mine.channel());
        assertEquals(DirectChannel.of(id), theirs.channel());
        assertTrue(((MessageView) mine.message()).mine());
        assertFalse(((MessageView) theirs.message()).mine());
        verify(realtimePublisher, never()).publishToUser(eq(BOB), any());
    }
}

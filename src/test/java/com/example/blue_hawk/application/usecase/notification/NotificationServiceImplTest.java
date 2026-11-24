package com.example.blue_hawk.application.usecase.notification;

import com.example.blue_hawk.domain.entity.notification.Notification;
import com.example.blue_hawk.domain.repository.INotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificationServiceImplTest {

    private static final UUID RECIPIENT = UUID.randomUUID();
    private static final UUID SENDER = UUID.randomUUID();
    private static final UUID OTHER_SENDER = UUID.randomUUID();
    private static final UUID MATCH = UUID.randomUUID();
    private static final UUID NEGOTIATION = UUID.randomUUID();
    private static final UUID CONVERSATION = UUID.randomUUID();

    @Mock
    private INotificationRepository repository;

    private NotificationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new NotificationServiceImpl(repository);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void createsWithAuditColumnsFromTheActor() {
        service.notifyMatch(RECIPIENT, Notification.Type.JOIN_REQUEST_RECEIVED, SENDER, MATCH);

        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(repository).save(saved.capture());
        Notification n = saved.getValue();
        assertEquals(RECIPIENT, n.getUserId());
        assertEquals(SENDER, n.getActorId());
        assertEquals(SENDER, n.getCreatedBy(), "created_by = quem causou");
        assertNotNull(n.getCreatedAt());
        assertNull(n.getUpdatedAt());
        assertNull(n.getReadAt());
    }

    @Test
    void neverNotifiesTheActorThemselves() {
        service.notifyMatch(SENDER, Notification.Type.JOIN_REQUEST_RECEIVED, SENDER, MATCH);
        service.notifyMessage(SENDER, SENDER, MATCH, null);

        verify(repository, never()).save(any());
    }

    @Test
    void aggregatesUnreadMessagesOfTheSameThread() {
        Notification existing = Notification.create(RECIPIENT, Notification.Type.MATCH_MESSAGE, SENDER, MATCH, null,
                null);
        when(repository.findUnreadMessage(RECIPIENT, MATCH, null)).thenReturn(Optional.of(existing));

        service.notifyMessage(RECIPIENT, OTHER_SENDER, MATCH, null);

        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(repository).save(saved.capture());
        assertSame(existing, saved.getValue(), "atualiza a mesma linha, sem criar outra");
        assertEquals(2, existing.getCount());
        assertEquals(OTHER_SENDER, existing.getActorId(), "mostra o último remetente");
        assertEquals(OTHER_SENDER, existing.getUpdatedBy());
        assertNotNull(existing.getUpdatedAt());
    }

    @Test
    void startsANewNotificationWhenThereIsNoUnreadOne() {
        when(repository.findUnreadMessage(RECIPIENT, MATCH, SENDER)).thenReturn(Optional.empty());

        service.notifyMessage(RECIPIENT, SENDER, MATCH, SENDER);

        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(repository).save(saved.capture());
        assertEquals(Notification.Type.MATCH_MESSAGE, saved.getValue().getType());
        assertEquals(SENDER, saved.getValue().getThreadUserId());
        assertEquals(1, saved.getValue().getCount());
    }

    // ── negociações da Loja ─────────────────────────────────────────────────

    @Test
    void offerMessageStartsANotificationLinkedToTheNegotiation() {
        when(repository.findUnreadNegotiationMessage(RECIPIENT, NEGOTIATION)).thenReturn(Optional.empty());

        service.notifyOfferMessage(RECIPIENT, SENDER, NEGOTIATION);

        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(repository).save(saved.capture());
        Notification n = saved.getValue();
        assertEquals(Notification.Type.OFFER_MESSAGE, n.getType());
        assertEquals(NEGOTIATION, n.getNegotiationId());
        assertEquals(SENDER, n.getActorId());
        assertEquals(SENDER, n.getCreatedBy());
        assertNull(n.getOpenMatchId());
        assertEquals(1, n.getCount());
    }

    @Test
    void aggregatesUnreadOfferMessagesOfTheSameNegotiation() {
        Notification existing = Notification.createForNegotiation(RECIPIENT, Notification.Type.OFFER_MESSAGE, SENDER,
                NEGOTIATION);
        when(repository.findUnreadNegotiationMessage(RECIPIENT, NEGOTIATION)).thenReturn(Optional.of(existing));

        service.notifyOfferMessage(RECIPIENT, OTHER_SENDER, NEGOTIATION);

        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(repository).save(saved.capture());
        assertSame(existing, saved.getValue(), "atualiza a mesma linha, sem criar outra");
        assertEquals(2, existing.getCount());
        assertEquals(OTHER_SENDER, existing.getActorId());
        assertEquals(OTHER_SENDER, existing.getUpdatedBy());
        assertNotNull(existing.getUpdatedAt());
    }

    @Test
    void neverNotifiesTheActorOfANegotiation() {
        service.notifyOfferMessage(SENDER, SENDER, NEGOTIATION);
        service.notifyNegotiation(SENDER, Notification.Type.OFFER_ACCEPTED, SENDER, NEGOTIATION);

        verify(repository, never()).save(any());
    }

    @Test
    void readingANegotiationMarksItsOfferMessagesRead() {
        service.markNegotiationRead(RECIPIENT, NEGOTIATION);

        verify(repository).markNegotiationMessagesRead(eq(RECIPIENT), eq(NEGOTIATION), any());
    }

    // ── mensagens diretas ───────────────────────────────────────────────────

    @Test
    void directMessageStartsANotificationLinkedToTheConversation() {
        when(repository.findUnreadDirectMessage(RECIPIENT, CONVERSATION)).thenReturn(Optional.empty());

        service.notifyDirectMessage(RECIPIENT, SENDER, CONVERSATION);

        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(repository).save(saved.capture());
        Notification n = saved.getValue();
        assertEquals(Notification.Type.DIRECT_MESSAGE, n.getType());
        assertEquals(CONVERSATION, n.getDirectConversationId());
        assertEquals(SENDER, n.getActorId());
        assertEquals(SENDER, n.getCreatedBy());
        assertNull(n.getOpenMatchId());
        assertNull(n.getNegotiationId());
        assertEquals(1, n.getCount());
    }

    @Test
    void aggregatesUnreadDirectMessagesOfTheSameConversation() {
        Notification existing = Notification.createForDirect(RECIPIENT, SENDER, CONVERSATION);
        when(repository.findUnreadDirectMessage(RECIPIENT, CONVERSATION)).thenReturn(Optional.of(existing));

        service.notifyDirectMessage(RECIPIENT, SENDER, CONVERSATION);
        service.notifyDirectMessage(SENDER, SENDER, CONVERSATION);

        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(repository).save(saved.capture());
        assertSame(existing, saved.getValue(), "atualiza a mesma linha, sem criar outra");
        assertEquals(2, existing.getCount());
        assertEquals(SENDER, existing.getUpdatedBy());
    }

    @Test
    void readingADirectConversationMarksItsMessagesRead() {
        service.markDirectConversationRead(RECIPIENT, CONVERSATION);

        verify(repository).markDirectMessagesRead(eq(RECIPIENT), eq(CONVERSATION), any());
    }
}

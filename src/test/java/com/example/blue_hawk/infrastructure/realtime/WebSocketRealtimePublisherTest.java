package com.example.blue_hawk.infrastructure.realtime;

import com.example.blue_hawk.application.dto.realtime.RealtimeEvents.ChatMessage;
import com.example.blue_hawk.application.dto.realtime.RealtimeEvents.MatchChannel;
import com.example.blue_hawk.application.dto.realtime.RealtimeEvents.NotificationsChanged;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebSocketRealtimePublisherTest {

    private static final UUID ANA = UUID.randomUUID();
    private static final UUID BOB = UUID.randomUUID();

    private final RealtimeSessionRegistry registry = new RealtimeSessionRegistry();
    private final WebSocketRealtimePublisher publisher = new WebSocketRealtimePublisher(registry);

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private static WebSocketSession session() {
        WebSocketSession session = mock(WebSocketSession.class);
        Map<String, Object> attributes = new HashMap<>();
        when(session.getAttributes()).thenReturn(attributes);
        when(session.isOpen()).thenReturn(true);
        when(session.getId()).thenReturn(UUID.randomUUID().toString());
        return session;
    }

    @Test
    void registryKeepsSeveralTabsPerUserAndRemovesOnClose() throws Exception {
        WebSocketSession tab1 = session();
        WebSocketSession tab2 = session();
        registry.add(ANA, tab1);
        registry.add(ANA, tab2);
        assertEquals(2, registry.sessionsOf(ANA).size());

        registry.sendToUser(ANA, "{}");
        verify(tab1).sendMessage(new TextMessage("{}"));
        verify(tab2).sendMessage(new TextMessage("{}"));

        registry.remove(tab1);
        assertEquals(1, registry.sessionsOf(ANA).size());
        registry.remove(tab2);
        assertTrue(registry.sessionsOf(ANA).isEmpty());
    }

    @Test
    void withoutTransactionSendsRightAwayAsJson() throws Exception {
        WebSocketSession ana = session();
        registry.add(ANA, ana);

        publisher.publishToUser(ANA, ChatMessage.of(MatchChannel.of("m1", null), Map.of("id", "x")));

        verify(ana).sendMessage(new TextMessage(
                "{\"type\":\"chat.message\",\"channel\":{\"kind\":\"MATCH\",\"matchId\":\"m1\",\"withUserId\":null},"
                        + "\"message\":{\"id\":\"x\"}}"));
    }

    @Test
    void insideATransactionSendsOnlyAfterCommitAndOncePerUser() throws Exception {
        WebSocketSession ana = session();
        WebSocketSession bob = session();
        registry.add(ANA, ana);
        registry.add(BOB, bob);
        TransactionSynchronizationManager.initSynchronization();

        publisher.publishToUser(ANA, NotificationsChanged.INSTANCE);
        publisher.publishToUsers(List.of(ANA, BOB), NotificationsChanged.INSTANCE);
        verify(ana, never()).sendMessage(any());

        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        synchronizations.forEach(TransactionSynchronization::afterCommit);
        synchronizations.forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));

        TextMessage expected = new TextMessage("{\"type\":\"notifications.changed\"}");
        verify(ana, times(1)).sendMessage(expected);
        verify(bob, times(1)).sendMessage(expected);
    }

    @Test
    void rollbackSendsNothing() throws Exception {
        WebSocketSession ana = session();
        registry.add(ANA, ana);
        TransactionSynchronizationManager.initSynchronization();

        publisher.publishToUser(ANA, NotificationsChanged.INSTANCE);
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        verify(ana, never()).sendMessage(any());
    }

    @Test
    void aBrokenConnectionNeverBreaksThePublisher() throws Exception {
        WebSocketSession broken = session();
        WebSocketSession ok = session();
        doThrow(new java.io.IOException("boom")).when(broken).sendMessage(any());
        registry.add(ANA, broken);
        registry.add(ANA, ok);

        publisher.publishToUser(ANA, NotificationsChanged.INSTANCE);

        verify(ok).sendMessage(new TextMessage("{\"type\":\"notifications.changed\"}"));
    }
}

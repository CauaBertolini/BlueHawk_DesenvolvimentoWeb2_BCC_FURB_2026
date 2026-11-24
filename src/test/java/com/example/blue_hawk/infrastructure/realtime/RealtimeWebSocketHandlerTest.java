package com.example.blue_hawk.infrastructure.realtime;

import com.example.blue_hawk.infrastructure.security.TokenBlacklistService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RealtimeWebSocketHandlerTest {

    private static final UUID USER = UUID.randomUUID();

    private JwtDecoder jwtDecoder;
    private TokenBlacklistService blacklist;
    private RealtimeSessionRegistry registry;
    private ScheduledExecutorService scheduler;
    private RealtimeWebSocketHandler handler;

    @BeforeEach
    void setUp() {
        jwtDecoder = mock(JwtDecoder.class);
        blacklist = new TokenBlacklistService();
        registry = new RealtimeSessionRegistry();
        scheduler = mock(ScheduledExecutorService.class);
        handler = new RealtimeWebSocketHandler(jwtDecoder, blacklist, registry, scheduler, Duration.ofSeconds(10));
        when(jwtDecoder.decode("valid")).thenReturn(jwt("valid", "jti-1"));
        when(jwtDecoder.decode("invalid")).thenThrow(new BadJwtException("bad"));
    }

    private static Jwt jwt(String token, String jti) {
        return Jwt.withTokenValue(token).header("alg", "HS256").subject(USER.toString()).jti(jti)
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(600)).build();
    }

    private static WebSocketSession session() throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        Map<String, Object> attributes = new HashMap<>();
        when(session.getAttributes()).thenReturn(attributes);
        when(session.isOpen()).thenReturn(true);
        when(session.getId()).thenReturn(UUID.randomUUID().toString());
        return session;
    }

    private static List<String> sent(WebSocketSession session, int times) throws Exception {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<WebSocketMessage<?>> captor = ArgumentCaptor.forClass(WebSocketMessage.class);
        verify(session, times(times)).sendMessage(captor.capture());
        return captor.getAllValues().stream().map(m -> ((TextMessage) m).getPayload()).toList();
    }

    @Test
    void validTokenAuthenticatesRepliesReadyAndRegisters() throws Exception {
        WebSocketSession session = session();
        handler.afterConnectionEstablished(session);

        handler.handleTextMessage(session, new TextMessage("{\"type\":\"auth\",\"token\":\"valid\"}"));

        assertEquals(List.of("{\"type\":\"ready\"}"), sent(session, 1));
        assertEquals(1, registry.sessionsOf(USER).size());
        verify(session, never()).close(any());
    }

    @Test
    void invalidOrMissingTokenClosesWith4401() throws Exception {
        WebSocketSession invalid = session();
        handler.handleTextMessage(invalid, new TextMessage("{\"type\":\"auth\",\"token\":\"invalid\"}"));
        verify(invalid).close(RealtimeWebSocketHandler.UNAUTHORIZED);

        WebSocketSession missing = session();
        handler.handleTextMessage(missing, new TextMessage("{\"type\":\"auth\"}"));
        verify(missing).close(RealtimeWebSocketHandler.UNAUTHORIZED);

        assertEquals(4401, RealtimeWebSocketHandler.UNAUTHORIZED.getCode());
        assertTrue(registry.sessionsOf(USER).isEmpty());
    }

    @Test
    void blacklistedTokenClosesWith4401() throws Exception {
        blacklist.blacklist("jti-1", Instant.now().plusSeconds(600));
        WebSocketSession session = session();

        handler.handleTextMessage(session, new TextMessage("{\"type\":\"auth\",\"token\":\"valid\"}"));

        verify(session).close(RealtimeWebSocketHandler.UNAUTHORIZED);
        assertTrue(registry.sessionsOf(USER).isEmpty());
    }

    @Test
    void messagesBeforeAuthAreIgnoredAndPingAfterAuthGetsPong() throws Exception {
        WebSocketSession session = session();
        handler.handleTextMessage(session, new TextMessage("{\"type\":\"ping\"}"));
        handler.handleTextMessage(session, new TextMessage("não é json"));
        verify(session, never()).sendMessage(any());
        verify(session, never()).close(any());

        handler.handleTextMessage(session, new TextMessage("{\"type\":\"auth\",\"token\":\"valid\"}"));
        handler.handleTextMessage(session, new TextMessage("{\"type\":\"ping\"}"));

        assertEquals(List.of("{\"type\":\"ready\"}", "{\"type\":\"pong\"}"), sent(session, 2));
    }

    @Test
    void closesWhenNoAuthArrivesInTime() throws Exception {
        WebSocketSession late = session();
        WebSocketSession authed = session();
        ArgumentCaptor<Runnable> timeout = ArgumentCaptor.forClass(Runnable.class);

        handler.afterConnectionEstablished(late);
        handler.afterConnectionEstablished(authed);
        handler.handleTextMessage(authed, new TextMessage("{\"type\":\"auth\",\"token\":\"valid\"}"));
        verify(scheduler, times(2)).schedule(timeout.capture(), eq(10_000L), eq(TimeUnit.MILLISECONDS));
        timeout.getAllValues().forEach(Runnable::run);

        verify(late).close(RealtimeWebSocketHandler.UNAUTHORIZED);
        verify(authed, never()).close(any());
    }

    @Test
    void closingRemovesTheSessionFromTheRegistry() throws Exception {
        WebSocketSession session = session();
        handler.handleTextMessage(session, new TextMessage("{\"type\":\"auth\",\"token\":\"valid\"}"));

        handler.afterConnectionClosed(session, CloseStatus.NORMAL);

        assertTrue(registry.sessionsOf(USER).isEmpty());
        verify(scheduler, never()).schedule(any(Runnable.class), anyLong(), any());
    }
}

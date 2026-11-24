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

    private static final String AUTH_VALID = "{\"type\":\"auth\",\"token\":\"valid\"}";

    private RealtimeWebSocketHandler limited(RealtimeWebSocketHandler.Limits limits,
                                             java.util.function.Predicate<UUID> userExists) {
        return new RealtimeWebSocketHandler(jwtDecoder, blacklist, registry, scheduler, Duration.ofSeconds(10),
                userExists, limits);
    }

    private static RealtimeWebSocketHandler.Limits limits(int total, int perIp, int perUser, int messages) {
        return new RealtimeWebSocketHandler.Limits(total, perIp, perUser, messages, Duration.ZERO);
    }

    private static Jwt jwtFor(UUID user, String token, Instant expiresAt) {
        return Jwt.withTokenValue(token).header("alg", "HS256").subject(user.toString()).jti("jti-" + token)
                .issuedAt(expiresAt.minusSeconds(600)).expiresAt(expiresAt).build();
    }

    @Test
    void connectionClosesWhenItsTokenExpiresUnlessRenewed() throws Exception {
        when(jwtDecoder.decode("short")).thenReturn(jwtFor(USER, "short", Instant.now().minusSeconds(1)));
        WebSocketSession expired = session();
        WebSocketSession renewed = session();
        handler.handleTextMessage(expired, new TextMessage("{\"type\":\"auth\",\"token\":\"short\"}"));
        handler.handleTextMessage(renewed, new TextMessage("{\"type\":\"auth\",\"token\":\"short\"}"));
        // O cliente renova na mesma conexão com o token novo: sem resposta, só estende a validade
        handler.handleTextMessage(renewed, new TextMessage(AUTH_VALID));

        handler.closeExpired();

        verify(expired).close(RealtimeWebSocketHandler.UNAUTHORIZED);
        verify(renewed, never()).close(any());
        assertEquals(1, registry.sessionsOf(USER).size());
        assertEquals(List.of("{\"type\":\"ready\"}"), sent(renewed, 1));
    }

    @Test
    void renewalWithAnotherUsersTokenClosesAndInvalidRenewalIsIgnored() throws Exception {
        when(jwtDecoder.decode("other")).thenReturn(jwtFor(UUID.randomUUID(), "other", Instant.now().plusSeconds(600)));
        WebSocketSession session = session();
        handler.handleTextMessage(session, new TextMessage(AUTH_VALID));

        handler.handleTextMessage(session, new TextMessage("{\"type\":\"auth\",\"token\":\"invalid\"}"));
        verify(session, never()).close(any());

        handler.handleTextMessage(session, new TextMessage("{\"type\":\"auth\",\"token\":\"other\"}"));
        verify(session).close(RealtimeWebSocketHandler.UNAUTHORIZED);
    }

    @Test
    void tokenOfADeletedAccountDoesNotAuthenticate() throws Exception {
        RealtimeWebSocketHandler strict = limited(limits(10, 10, 10, 10), id -> false);
        WebSocketSession session = session();

        strict.handleTextMessage(session, new TextMessage(AUTH_VALID));

        verify(session).close(RealtimeWebSocketHandler.UNAUTHORIZED);
        assertTrue(registry.sessionsOf(USER).isEmpty());
    }

    @Test
    void connectionLimitsPerIpAndInTotalAreEnforcedAndFreedOnClose() throws Exception {
        RealtimeWebSocketHandler perIp = limited(limits(10, 1, 10, 10), id -> true);
        WebSocketSession first = session();
        WebSocketSession second = session();
        perIp.afterConnectionEstablished(first);
        perIp.afterConnectionEstablished(second);
        verify(first, never()).close(any());
        verify(second).close(RealtimeWebSocketHandler.TOO_MANY_CONNECTIONS);

        // Fechar libera a vaga (e fechar duas vezes não libera duas)
        perIp.afterConnectionClosed(first, CloseStatus.NORMAL);
        perIp.afterConnectionClosed(first, CloseStatus.NORMAL);
        WebSocketSession third = session();
        WebSocketSession fourth = session();
        perIp.afterConnectionEstablished(third);
        perIp.afterConnectionEstablished(fourth);
        verify(third, never()).close(any());
        verify(fourth).close(RealtimeWebSocketHandler.TOO_MANY_CONNECTIONS);

        RealtimeWebSocketHandler total = limited(limits(1, 10, 10, 10), id -> true);
        WebSocketSession a = session();
        a.getAttributes().put(RealtimeWebSocketHandler.IP_ATTRIBUTE, "1.1.1.1");
        WebSocketSession b = session();
        b.getAttributes().put(RealtimeWebSocketHandler.IP_ATTRIBUTE, "2.2.2.2");
        total.afterConnectionEstablished(a);
        total.afterConnectionEstablished(b);
        verify(b).close(RealtimeWebSocketHandler.TOO_MANY_CONNECTIONS);
    }

    @Test
    void perUserConnectionLimitRejectsTheExtraTab() throws Exception {
        RealtimeWebSocketHandler perUser = limited(limits(10, 10, 1, 10), id -> true);
        WebSocketSession tab1 = session();
        WebSocketSession tab2 = session();

        perUser.handleTextMessage(tab1, new TextMessage(AUTH_VALID));
        perUser.handleTextMessage(tab2, new TextMessage(AUTH_VALID));

        verify(tab1, never()).close(any());
        verify(tab2).close(RealtimeWebSocketHandler.TOO_MANY_CONNECTIONS);
        assertEquals(1, registry.sessionsOf(USER).size());
    }

    @Test
    void messageFloodClosesTheConnection() throws Exception {
        RealtimeWebSocketHandler strict = limited(limits(10, 10, 10, 3), id -> true);
        WebSocketSession session = session();
        for (int i = 0; i < 3; i++) {
            strict.handleTextMessage(session, new TextMessage("{\"type\":\"ping\"}"));
        }
        verify(session, never()).close(any());

        strict.handleTextMessage(session, new TextMessage("{\"type\":\"ping\"}"));
        verify(session).close(CloseStatus.POLICY_VIOLATION);
    }

    @Test
    void deletedAccountHasItsConnectionsClosed() throws Exception {
        WebSocketSession session = session();
        handler.handleTextMessage(session, new TextMessage(AUTH_VALID));

        new WebSocketRealtimePublisher(registry).disconnectUser(USER);

        verify(session).close(RealtimeWebSocketHandler.UNAUTHORIZED);
        assertTrue(registry.sessionsOf(USER).isEmpty());
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

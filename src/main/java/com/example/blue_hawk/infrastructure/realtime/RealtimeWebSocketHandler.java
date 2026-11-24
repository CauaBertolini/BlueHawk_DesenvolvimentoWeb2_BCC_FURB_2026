package com.example.blue_hawk.infrastructure.realtime;

import com.example.blue_hawk.infrastructure.security.TokenBlacklistService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * WebSocket em {@code /ws}. O token NÃO vai na URL: depois de conectar, o cliente manda
 * {@code {"type":"auth","token":"<JWT>"}}. O token é validado como no HTTP (assinatura, validade e lista de tokens
 * invalidados); deu certo → {@code {"type":"ready"}}; token inválido, ou nenhum auth válido em 10 s → fecha com
 * 4401. Antes do auth, qualquer outra mensagem é ignorada. Depois: {@code {"type":"ping"}} → {@code {"type":"pong"}}.
 * O servidor só envia eventos (ver {@code RealtimeEvents}); o cliente não publica nada.
 */
@Component
public class RealtimeWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(RealtimeWebSocketHandler.class);

    /** Código de fechamento para autenticação ausente/inválida */
    public static final CloseStatus UNAUTHORIZED = new CloseStatus(4401, "Unauthorized");
    static final Duration AUTH_TIMEOUT = Duration.ofSeconds(10);
    static final String READY = "{\"type\":\"ready\"}";
    static final String PONG = "{\"type\":\"pong\"}";

    private final JwtDecoder jwtDecoder;
    private final TokenBlacklistService tokenBlacklistService;
    private final RealtimeSessionRegistry registry;
    private final ScheduledExecutorService scheduler;
    private final Duration authTimeout;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public RealtimeWebSocketHandler(JwtDecoder jwtDecoder, TokenBlacklistService tokenBlacklistService,
                                    RealtimeSessionRegistry registry) {
        this(jwtDecoder, tokenBlacklistService, registry, Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "ws-auth-timeout");
            thread.setDaemon(true);
            return thread;
        }), AUTH_TIMEOUT);
    }

    RealtimeWebSocketHandler(JwtDecoder jwtDecoder, TokenBlacklistService tokenBlacklistService,
                             RealtimeSessionRegistry registry, ScheduledExecutorService scheduler,
                             Duration authTimeout) {
        this.jwtDecoder = jwtDecoder;
        this.tokenBlacklistService = tokenBlacklistService;
        this.registry = registry;
        this.scheduler = scheduler;
        this.authTimeout = authTimeout;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        // Sem auth válido dentro do prazo: fecha
        scheduler.schedule(() -> {
            if (!isAuthenticated(session)) closeUnauthorized(session);
        }, authTimeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        JsonNode json;
        try {
            json = objectMapper.readTree(message.getPayload());
        } catch (Exception e) {
            return; // JSON inválido: ignora
        }
        String type = json != null && json.hasNonNull("type") ? json.get("type").asText() : "";

        if (!isAuthenticated(session)) {
            if ("auth".equals(type)) authenticate(session, json.hasNonNull("token") ? json.get("token").asText()
                    : null);
            return; // antes do auth, o resto é ignorado
        }
        if ("ping".equals(type)) {
            RealtimeSessionRegistry.send(sender(session), PONG);
        }
    }

    private void authenticate(WebSocketSession session, String token) {
        UUID userId = validate(token);
        if (userId == null) {
            closeUnauthorized(session);
            return;
        }
        WebSocketSession sender = registry.add(userId, session);
        RealtimeSessionRegistry.send(sender, READY);
    }

    /** Id do usuário do token, ou null se inválido, vencido ou invalidado (logout/rotação) */
    UUID validate(String token) {
        if (token == null || token.isBlank()) return null;
        try {
            // O JwtDecoder da API já confere assinatura, validade e a lista de invalidados
            Jwt jwt = jwtDecoder.decode(token);
            if ((jwt.getId() != null && tokenBlacklistService.isBlacklisted(jwt.getId()))
                    || tokenBlacklistService.isBlacklisted(token)) {
                return null;
            }
            return UUID.fromString(jwt.getSubject());
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        registry.remove(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        registry.remove(session);
    }

    private static boolean isAuthenticated(WebSocketSession session) {
        return session.getAttributes().get(RealtimeSessionRegistry.USER_ATTRIBUTE) != null;
    }

    private static WebSocketSession sender(WebSocketSession session) {
        Object sender = session.getAttributes().get(RealtimeSessionRegistry.SENDER_ATTRIBUTE);
        return sender instanceof WebSocketSession s ? s : session;
    }

    private void closeUnauthorized(WebSocketSession session) {
        try {
            if (session.isOpen()) session.close(UNAUTHORIZED);
        } catch (Exception e) {
            log.debug("Falha ao fechar o WebSocket {}: {}", session.getId(), e.getMessage());
        }
    }
}

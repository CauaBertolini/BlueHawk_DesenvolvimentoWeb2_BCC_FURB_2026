package com.example.blue_hawk.infrastructure.realtime;

import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Conexões abertas e autenticadas: usuário → sessões (um usuário pode ter várias abas). Thread-safe.
 *
 * <p>Cada sessão fica embrulhada num {@link ConcurrentWebSocketSessionDecorator}: {@code WebSocketSession} não é
 * thread-safe para envio, e o decorator serializa os envios (com limite de tempo e de buffer, para um cliente lento
 * não travar quem publica).</p>
 */
@Component
public class RealtimeSessionRegistry {

    private static final Logger log = LoggerFactory.getLogger(RealtimeSessionRegistry.class);

    /** Atributo da sessão com o id do usuário autenticado */
    static final String USER_ATTRIBUTE = "realtime.userId";
    /** Atributo da sessão com o decorator usado para enviar */
    static final String SENDER_ATTRIBUTE = "realtime.sender";
    static final int SEND_TIME_LIMIT_MS = 5_000;
    static final int BUFFER_SIZE_LIMIT = 512 * 1024;

    private final Map<UUID, Set<WebSocketSession>> sessionsByUser = new ConcurrentHashMap<>();

    /** Registra a sessão do usuário e devolve o decorator thread-safe usado para enviar a ela */
    public WebSocketSession add(UUID userId, WebSocketSession session) {
        WebSocketSession sender = new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MS,
                BUFFER_SIZE_LIMIT);
        session.getAttributes().put(USER_ATTRIBUTE, userId);
        session.getAttributes().put(SENDER_ATTRIBUTE, sender);
        sessionsByUser.computeIfAbsent(userId, id -> ConcurrentHashMap.newKeySet()).add(sender);
        return sender;
    }

    /** Tira a sessão (fechada ou com erro); o usuário sai do mapa quando não sobra nenhuma */
    public void remove(WebSocketSession session) {
        Object userId = session.getAttributes().get(USER_ATTRIBUTE);
        Object sender = session.getAttributes().get(SENDER_ATTRIBUTE);
        if (!(userId instanceof UUID id) || sender == null) return;
        sessionsByUser.computeIfPresent(id, (key, set) -> {
            set.remove(sender);
            return set.isEmpty() ? null : set;
        });
    }

    /** Sessões abertas do usuário (cópia) */
    public Set<WebSocketSession> sessionsOf(UUID userId) {
        Set<WebSocketSession> sessions = sessionsByUser.get(userId);
        return sessions == null ? Set.of() : Set.copyOf(sessions);
    }

    /** Envia o texto para todas as conexões do usuário; falha numa conexão não afeta as outras */
    public void sendToUser(UUID userId, String payload) {
        for (WebSocketSession session : sessionsOf(userId)) {
            send(session, payload);
        }
    }

    /** Percorre todas as conexões autenticadas (cópia: a ação pode remover sessões) */
    public void forEachSession(Consumer<WebSocketSession> action) {
        sessionsByUser.values().forEach(sessions -> Set.copyOf(sessions).forEach(action));
    }

    /** Fecha todas as conexões do usuário (conta excluída) e tira do registro */
    public void closeUser(UUID userId, CloseStatus status) {
        Set<WebSocketSession> sessions = sessionsByUser.remove(userId);
        if (sessions != null) {
            sessions.forEach(session -> close(session, status));
        }
    }

    /** Fechamento protegido: erro só vira log */
    static void close(WebSocketSession session, CloseStatus status) {
        try {
            if (session.isOpen()) session.close(status);
        } catch (Exception e) {
            log.debug("Falha ao fechar o WebSocket {}: {}", session.getId(), e.getMessage());
        }
    }

    /** Envio protegido: erro só vira log (e a sessão quebrada sai do registro) */
    static void send(WebSocketSession session, String payload) {
        try {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(payload));
            }
        } catch (Exception e) {
            log.debug("Falha ao enviar pelo WebSocket {}: {}", session.getId(), e.getMessage());
        }
    }
}

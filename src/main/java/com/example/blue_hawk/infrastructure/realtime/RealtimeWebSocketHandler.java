package com.example.blue_hawk.infrastructure.realtime;

import com.example.blue_hawk.domain.repository.IUserRepository;
import com.example.blue_hawk.infrastructure.security.TokenBlacklistService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.adapter.standard.StandardWebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

/**
 * WebSocket em {@code /ws}. O token NÃO vai na URL: depois de conectar, o cliente manda
 * {@code {"type":"auth","token":"<JWT>"}}. O token é validado como no HTTP (assinatura, validade, lista de tokens
 * invalidados e usuário ainda existente); deu certo → {@code {"type":"ready"}}; token inválido, ou nenhum auth válido
 * em 10 s → fecha com 4401. Antes do auth, qualquer outra mensagem é ignorada. Depois: {@code {"type":"ping"}} →
 * {@code {"type":"pong"}}. O servidor só envia eventos (ver {@code RealtimeEvents}); o cliente não publica nada.
 *
 * <p><b>A conexão vale só enquanto o token vale.</b> Quando o token usado no auth vence, o socket fecha com 4401.
 * Para continuar, o cliente manda outro {@code auth} com o token renovado (do mesmo usuário) na mesma conexão:
 * a validade é estendida, sem resposta. Um auth inválido numa conexão já autenticada é ignorado.</p>
 *
 * <p><b>Limites</b> ({@code app.realtime.*}): conexões no total, por IP e por usuário (acima disso fecha com 4429),
 * mensagens por minuto por conexão (fecha com 1008), tamanho da mensagem e tempo máximo sem tráfego.</p>
 */
@Component
public class RealtimeWebSocketHandler extends TextWebSocketHandler {

    /** Limites de uso do socket; ver {@code app.realtime.*} */
    public record Limits(int maxConnections, int maxPerIp, int maxPerUser, int maxMessagesPerMinute,
                         Duration idleTimeout) {
        static Limits unlimited() {
            return new Limits(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE,
                    Duration.ZERO);
        }
    }

    private static final Logger log = LoggerFactory.getLogger(RealtimeWebSocketHandler.class);

    /** Código de fechamento para autenticação ausente/inválida/vencida */
    public static final CloseStatus UNAUTHORIZED = new CloseStatus(4401, "Unauthorized");
    /** Código de fechamento quando o limite de conexões foi atingido */
    public static final CloseStatus TOO_MANY_CONNECTIONS = new CloseStatus(4429, "Too many connections");

    /** IP do cliente, gravado no handshake pelo {@link WebSocketConfig} */
    static final String IP_ATTRIBUTE = "realtime.ip";
    /** Validade (Instant) do token que autenticou a sessão */
    static final String EXPIRES_ATTRIBUTE = "realtime.expiresAt";
    private static final String COUNTED_ATTRIBUTE = "realtime.countedIp";
    private static final String WINDOW_ATTRIBUTE = "realtime.messageWindow";

    static final Duration AUTH_TIMEOUT = Duration.ofSeconds(10);
    static final Duration EXPIRY_SWEEP = Duration.ofSeconds(30);
    /** As mensagens do cliente são minúsculas (auth com o JWT, ping) */
    static final int MAX_TEXT_BYTES = 8 * 1024;
    static final String READY = "{\"type\":\"ready\"}";
    static final String PONG = "{\"type\":\"pong\"}";

    private final JwtDecoder jwtDecoder;
    private final TokenBlacklistService tokenBlacklistService;
    private final RealtimeSessionRegistry registry;
    private final ScheduledExecutorService scheduler;
    private final Duration authTimeout;
    private final Predicate<UUID> userExists;
    private final Limits limits;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final AtomicInteger connections = new AtomicInteger();
    private final Map<String, Integer> connectionsByIp = new ConcurrentHashMap<>();

    @Autowired
    public RealtimeWebSocketHandler(JwtDecoder jwtDecoder, TokenBlacklistService tokenBlacklistService,
                                    RealtimeSessionRegistry registry, IUserRepository userRepository,
                                    @Value("${app.realtime.max-connections:5000}") int maxConnections,
                                    @Value("${app.realtime.max-connections-per-ip:100}") int maxPerIp,
                                    @Value("${app.realtime.max-connections-per-user:8}") int maxPerUser,
                                    @Value("${app.realtime.max-messages-per-minute:60}") int maxMessagesPerMinute,
                                    @Value("${app.realtime.idle-timeout-seconds:180}") long idleTimeoutSeconds) {
        this(jwtDecoder, tokenBlacklistService, registry, Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "ws-auth-timeout");
            thread.setDaemon(true);
            return thread;
        }), AUTH_TIMEOUT, userRepository::existsById, new Limits(maxConnections, maxPerIp, maxPerUser,
                maxMessagesPerMinute, Duration.ofSeconds(Math.max(0, idleTimeoutSeconds))));
        // Uma varredura periódica em vez de um agendamento por sessão: não segura sessão fechada na memória
        scheduler.scheduleWithFixedDelay(this::closeExpired, EXPIRY_SWEEP.toMillis(), EXPIRY_SWEEP.toMillis(),
                TimeUnit.MILLISECONDS);
    }

    /** Sem limites e sem consulta de usuário (testes) */
    RealtimeWebSocketHandler(JwtDecoder jwtDecoder, TokenBlacklistService tokenBlacklistService,
                             RealtimeSessionRegistry registry, ScheduledExecutorService scheduler,
                             Duration authTimeout) {
        this(jwtDecoder, tokenBlacklistService, registry, scheduler, authTimeout, id -> true, Limits.unlimited());
    }

    RealtimeWebSocketHandler(JwtDecoder jwtDecoder, TokenBlacklistService tokenBlacklistService,
                             RealtimeSessionRegistry registry, ScheduledExecutorService scheduler,
                             Duration authTimeout, Predicate<UUID> userExists, Limits limits) {
        this.jwtDecoder = jwtDecoder;
        this.tokenBlacklistService = tokenBlacklistService;
        this.registry = registry;
        this.scheduler = scheduler;
        this.authTimeout = authTimeout;
        this.userExists = userExists;
        this.limits = limits;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        if (!acquire(session)) {
            RealtimeSessionRegistry.close(session, TOO_MANY_CONNECTIONS);
            return;
        }
        applyTransportLimits(session);
        // Sem auth válido dentro do prazo: fecha
        scheduler.schedule(() -> {
            if (!isAuthenticated(session)) RealtimeSessionRegistry.close(session, UNAUTHORIZED);
        }, authTimeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        if (tooManyMessages(session)) {
            RealtimeSessionRegistry.close(session, CloseStatus.POLICY_VIOLATION);
            return;
        }
        JsonNode json;
        try {
            json = objectMapper.readTree(message.getPayload());
        } catch (Exception e) {
            return; // JSON inválido: ignora
        }
        String type = json != null && json.hasNonNull("type") ? json.get("type").asText() : "";

        if ("auth".equals(type)) {
            authenticate(session, json.hasNonNull("token") ? json.get("token").asText() : null);
            return;
        }
        if (!isAuthenticated(session)) {
            return; // antes do auth, o resto é ignorado
        }
        if ("ping".equals(type)) {
            RealtimeSessionRegistry.send(sender(session), PONG);
        }
    }

    private void authenticate(WebSocketSession session, String token) {
        Jwt jwt = decode(token);
        Object current = session.getAttributes().get(RealtimeSessionRegistry.USER_ATTRIBUTE);

        if (current != null) {
            // Renovação: só estende a validade, e só com um token válido do mesmo usuário
            if (jwt != null) {
                if (current.equals(UUID.fromString(jwt.getSubject()))) {
                    session.getAttributes().put(EXPIRES_ATTRIBUTE, expiry(jwt));
                } else {
                    RealtimeSessionRegistry.close(session, UNAUTHORIZED);
                }
            }
            return;
        }
        if (jwt == null) {
            RealtimeSessionRegistry.close(session, UNAUTHORIZED);
            return;
        }
        UUID userId = UUID.fromString(jwt.getSubject());
        if (registry.sessionsOf(userId).size() >= limits.maxPerUser()) {
            RealtimeSessionRegistry.close(session, TOO_MANY_CONNECTIONS);
            return;
        }
        session.getAttributes().put(EXPIRES_ATTRIBUTE, expiry(jwt));
        WebSocketSession sender = registry.add(userId, session);
        RealtimeSessionRegistry.send(sender, READY);
    }

    /** O token decodificado, ou null se inválido, vencido, invalidado (rotação) ou de conta que não existe mais */
    private Jwt decode(String token) {
        if (token == null || token.isBlank()) return null;
        try {
            // O JwtDecoder da API já confere assinatura, validade e a lista de invalidados
            Jwt jwt = jwtDecoder.decode(token);
            if ((jwt.getId() != null && tokenBlacklistService.isBlacklisted(jwt.getId()))
                    || tokenBlacklistService.isBlacklisted(token)) {
                return null;
            }
            // Como no HTTP (UserRoleJwtAuthenticationConverter): conta removida não autentica
            return userExists.test(UUID.fromString(jwt.getSubject())) ? jwt : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static Instant expiry(Jwt jwt) {
        return jwt.getExpiresAt() != null ? jwt.getExpiresAt() : Instant.now();
    }

    /** Fecha as conexões cujo token venceu sem ser renovado */
    void closeExpired() {
        try {
            Instant now = Instant.now();
            registry.forEachSession(session -> {
                Object expiresAt = session.getAttributes().get(EXPIRES_ATTRIBUTE);
                if (expiresAt instanceof Instant instant && !instant.isAfter(now)) {
                    registry.remove(session);
                    RealtimeSessionRegistry.close(session, UNAUTHORIZED);
                }
            });
        } catch (Exception e) {
            log.warn("Falha ao varrer conexões vencidas do WebSocket: {}", e.getMessage());
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        registry.remove(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        registry.remove(session);
        release(session);
    }

    // ── limites ────────────────────────────────────────────────────────────

    /** Conta a conexão no total e no IP; false se algum limite estourou */
    private boolean acquire(WebSocketSession session) {
        if (connections.incrementAndGet() > limits.maxConnections()) {
            connections.decrementAndGet();
            return false;
        }
        String ip = clientIp(session);
        boolean[] allowed = {true};
        connectionsByIp.compute(ip, (key, count) -> {
            int open = count == null ? 0 : count;
            if (open >= limits.maxPerIp()) {
                allowed[0] = false;
                return count;
            }
            return open + 1;
        });
        if (!allowed[0]) {
            connections.decrementAndGet();
            return false;
        }
        session.getAttributes().put(COUNTED_ATTRIBUTE, ip);
        return true;
    }

    /** Devolve a vaga da conexão (uma vez só, mesmo que o fechamento chegue duas vezes) */
    private void release(WebSocketSession session) {
        if (session.getAttributes().remove(COUNTED_ATTRIBUTE) instanceof String ip) {
            connections.decrementAndGet();
            connectionsByIp.computeIfPresent(ip, (key, count) -> count <= 1 ? null : count - 1);
        }
    }

    private static String clientIp(WebSocketSession session) {
        if (session.getAttributes().get(IP_ATTRIBUTE) instanceof String ip) {
            return ip;
        }
        return session.getRemoteAddress() != null && session.getRemoteAddress().getAddress() != null
                ? session.getRemoteAddress().getAddress().getHostAddress()
                : "unknown";
    }

    /** As mensagens de uma sessão chegam em série, então o contador não precisa de trava */
    private boolean tooManyMessages(WebSocketSession session) {
        long now = System.currentTimeMillis();
        long[] window = session.getAttributes().get(WINDOW_ATTRIBUTE) instanceof long[] w ? w : null;
        if (window == null || now - window[0] >= 60_000) {
            session.getAttributes().put(WINDOW_ATTRIBUTE, new long[]{now, 1});
            return false;
        }
        return ++window[1] > limits.maxMessagesPerMinute();
    }

    private void applyTransportLimits(WebSocketSession session) {
        try {
            session.setTextMessageSizeLimit(MAX_TEXT_BYTES);
            // Conexão morta (sem ping do cliente) não fica ocupando vaga
            if (!limits.idleTimeout().isZero() && session instanceof StandardWebSocketSession standard
                    && standard.getNativeSession() != null) {
                standard.getNativeSession().setMaxIdleTimeout(limits.idleTimeout().toMillis());
            }
        } catch (Exception e) {
            log.debug("Falha ao aplicar limites no WebSocket {}: {}", session.getId(), e.getMessage());
        }
    }

    // ── sessão ─────────────────────────────────────────────────────────────

    private static boolean isAuthenticated(WebSocketSession session) {
        return session.getAttributes().get(RealtimeSessionRegistry.USER_ATTRIBUTE) != null;
    }

    private static WebSocketSession sender(WebSocketSession session) {
        Object sender = session.getAttributes().get(RealtimeSessionRegistry.SENDER_ATTRIBUTE);
        return sender instanceof WebSocketSession s ? s : session;
    }

}

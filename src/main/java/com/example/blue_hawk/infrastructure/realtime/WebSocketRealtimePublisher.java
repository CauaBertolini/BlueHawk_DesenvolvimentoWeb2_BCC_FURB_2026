package com.example.blue_hawk.infrastructure.realtime;

import com.example.blue_hawk.application.port.RealtimeConnections;

import com.example.blue_hawk.application.port.RealtimePublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * {@link RealtimePublisher} pelo WebSocket.
 * <ul>
 *   <li>Com transação ativa, os eventos ficam pendentes e só saem depois do commit (rollback = nada é enviado).
 *   Dentro da mesma transação, o mesmo evento para o mesmo usuário sai uma vez só (ex.: várias notificações
 *   criadas para a mesma pessoa = um {@code notifications.changed}).</li>
 *   <li>Sem transação, envia na hora.</li>
 *   <li>Qualquer erro vira log: o push nunca derruba a requisição.</li>
 * </ul>
 */
@Component
public class WebSocketRealtimePublisher implements RealtimePublisher, RealtimeConnections {

    private static final Logger log = LoggerFactory.getLogger(WebSocketRealtimePublisher.class);
    /** Chave do recurso da transação com os envios pendentes (JSON → destinatários) */
    private static final Object PENDING_KEY = WebSocketRealtimePublisher.class.getName() + ".pending";

    private final RealtimeSessionRegistry registry;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public WebSocketRealtimePublisher(RealtimeSessionRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void publishToUsers(Collection<UUID> userIds, Object event) {
        try {
            if (userIds == null || userIds.isEmpty() || event == null) return;
            String payload = objectMapper.writeValueAsString(event);
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                pending().computeIfAbsent(payload, p -> new LinkedHashSet<>()).addAll(userIds);
            } else {
                send(Map.of(payload, new LinkedHashSet<>(userIds)));
            }
        } catch (Exception e) {
            log.warn("Falha ao publicar evento em tempo real: {}", e.getMessage());
        }
    }

    @Override
    public void disconnectUser(UUID userId) {
        try {
            if (userId != null) registry.closeUser(userId, RealtimeWebSocketHandler.UNAUTHORIZED);
        } catch (Exception e) {
            log.warn("Falha ao derrubar conexões em tempo real: {}", e.getMessage());
        }
    }

    /** Envios pendentes da transação atual; registra o envio pós-commit na primeira vez */
    @SuppressWarnings("unchecked")
    private Map<String, Set<UUID>> pending() {
        Map<String, Set<UUID>> pending =
                (Map<String, Set<UUID>>) TransactionSynchronizationManager.getResource(PENDING_KEY);
        if (pending != null) return pending;

        Map<String, Set<UUID>> created = new LinkedHashMap<>();
        TransactionSynchronizationManager.bindResource(PENDING_KEY, created);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                send(created);
            }

            @Override
            public void afterCompletion(int status) {
                if (TransactionSynchronizationManager.hasResource(PENDING_KEY)) {
                    TransactionSynchronizationManager.unbindResource(PENDING_KEY);
                }
            }
        });
        return created;
    }

    private void send(Map<String, Set<UUID>> byPayload) {
        byPayload.forEach((payload, users) -> users.forEach(userId -> {
            if (userId == null) return;
            try {
                registry.sendToUser(userId, payload);
            } catch (Exception e) {
                log.warn("Falha ao enviar evento em tempo real: {}", e.getMessage());
            }
        }));
    }
}

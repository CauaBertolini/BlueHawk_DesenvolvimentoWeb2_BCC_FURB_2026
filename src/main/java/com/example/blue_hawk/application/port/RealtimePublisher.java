package com.example.blue_hawk.application.port;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Push em tempo real para os usuários conectados (implementado na infraestrutura via WebSocket em {@code /ws}).
 *
 * <p>Chamado de dentro dos casos de uso: a entrega só acontece <b>depois do commit</b> da transação em curso
 * (sem transação, na hora). Usuário sem conexão aberta simplesmente não recebe. Falhar ao enviar nunca derruba a
 * requisição HTTP.</p>
 */
public interface RealtimePublisher {

    /** Envia o mesmo evento (serializado em JSON) para todas as conexões abertas de cada usuário */
    void publishToUsers(Collection<UUID> userIds, Object event);

    default void publishToUser(UUID userId, Object event) {
        if (userId != null) publishToUsers(List.of(userId), event);
    }
}

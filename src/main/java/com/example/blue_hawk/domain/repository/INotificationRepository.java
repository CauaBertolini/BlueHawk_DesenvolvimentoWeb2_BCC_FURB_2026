package com.example.blue_hawk.domain.repository;

import com.example.blue_hawk.domain.entity.notification.Notification;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface INotificationRepository {

    Notification save(Notification notification);

    Optional<Notification> findById(UUID id);

    /** Notificações do usuário, da última atividade para a mais antiga */
    List<Notification> findByUser(UUID userId, int page, int size);

    /** Quantas notificações não lidas (linhas, não a soma de {@code count}) */
    long countUnread(UUID userId);

    /** A notificação MATCH_MESSAGE não lida da conversa ({@code threadUserId} null = mesa), para agregar */
    Optional<Notification> findUnreadMessage(UUID userId, UUID openMatchId, UUID threadUserId);

    /** Todas as MATCH_MESSAGE não lidas do usuário na partida (mesa e conversas diretas) */
    List<Notification> findUnreadMessages(UUID userId, UUID openMatchId);

    /** Marca como lidas as MATCH_MESSAGE da conversa ({@code threadUserId} null = mesa). Devolve quantas. */
    int markMessagesRead(UUID userId, UUID openMatchId, UUID threadUserId, Instant readAt);

    /** A notificação OFFER_MESSAGE não lida da negociação, para agregar */
    Optional<Notification> findUnreadNegotiationMessage(UUID userId, UUID negotiationId);

    /** Marca como lidas as OFFER_MESSAGE da negociação. Devolve quantas. */
    int markNegotiationMessagesRead(UUID userId, UUID negotiationId, Instant readAt);

    /** A notificação DIRECT_MESSAGE não lida da conversa direta, para agregar */
    Optional<Notification> findUnreadDirectMessage(UUID userId, UUID directConversationId);

    /** Marca como lidas as DIRECT_MESSAGE da conversa direta. Devolve quantas. */
    int markDirectMessagesRead(UUID userId, UUID directConversationId, Instant readAt);

    /** Todas as MATCH_MESSAGE, OFFER_MESSAGE e DIRECT_MESSAGE não lidas do usuário (caixa de conversas) */
    List<Notification> findUnreadChatMessages(UUID userId);

    /** Quantas MATCH_MESSAGE, OFFER_MESSAGE e DIRECT_MESSAGE não lidas o usuário tem (linhas, não a soma de {@code count}) */
    long countUnreadChatMessages(UUID userId);

    /** Marca todas como lidas. Devolve quantas. */
    int markAllRead(UUID userId, Instant readAt);
}

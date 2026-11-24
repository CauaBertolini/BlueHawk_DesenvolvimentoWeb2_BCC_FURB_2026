package com.example.blue_hawk.application.usecase.notification;

import com.example.blue_hawk.domain.entity.notification.Notification;

import java.util.UUID;

/**
 * Cria as notificações disparadas pelos outros casos de uso, dentro da transação deles.
 * Nunca notifica quem causou o evento ({@code actorId}).
 */
public interface NotificationService {

    /** Pedidos do Jogue e partida cancelada */
    void notifyMatch(UUID recipientId, Notification.Type type, UUID actorId, UUID openMatchId);

    /** Revisão de jogo enviado: BOARDGAME_APPROVED / BOARDGAME_REJECTED */
    void notifyBoardgame(UUID recipientId, Notification.Type type, UUID actorId, UUID boardgameId);

    /**
     * Nova mensagem no chat. {@code threadUserId}: null para a mesa, o remetente numa conversa direta. Se o
     * destinatário já tem uma MATCH_MESSAGE não lida da mesma conversa, soma nela em vez de criar outra.
     */
    void notifyMessage(UUID recipientId, UUID senderId, UUID openMatchId, UUID threadUserId);

    /** Leu a conversa: marca as MATCH_MESSAGE dela como lidas. Devolve quantas. */
    int markThreadRead(UUID userId, UUID openMatchId, UUID threadUserId);

    /** Evento de uma negociação da Loja que não é mensagem: OFFER_ACCEPTED */
    void notifyNegotiation(UUID recipientId, Notification.Type type, UUID actorId, UUID negotiationId);

    /**
     * Nova mensagem numa negociação (OFFER_MESSAGE). Se o destinatário já tem uma não lida da mesma negociação,
     * soma nela em vez de criar outra (como em {@link #notifyMessage}).
     */
    void notifyOfferMessage(UUID recipientId, UUID senderId, UUID negotiationId);

    /** Leu a negociação: marca as OFFER_MESSAGE dela como lidas. Devolve quantas. */
    int markNegotiationRead(UUID userId, UUID negotiationId);

    /**
     * Nova mensagem direta (DIRECT_MESSAGE). Se o destinatário já tem uma não lida da mesma conversa, soma nela
     * em vez de criar outra (como em {@link #notifyMessage}).
     */
    void notifyDirectMessage(UUID recipientId, UUID senderId, UUID directConversationId);

    /** Leu a conversa direta: marca as DIRECT_MESSAGE dela como lidas. Devolve quantas. */
    int markDirectConversationRead(UUID userId, UUID directConversationId);
}

package com.example.blue_hawk.application.dto.openmatch;

import java.util.List;

/** DTOs do chat das partidas do Jogue (mesa e conversas diretas). Datas em ISO-8601 UTC. */
public final class OpenMatchChatDtos {

    private OpenMatchChatDtos() {
    }

    /** Integrante da mesa: o host e os aceitos */
    public record MemberView(String userId, String name, boolean isHost) {
    }

    public record LastMessageView(String body, String createdAt, String senderId) {
    }

    /**
     * Conversa direta. Para o host, uma por pessoa com pedido ({@code requestStatus} = status do pedido); para o
     * participante, a conversa com o host ({@code requestStatus} = "HOST") e, se ele está aceito, uma por outro
     * jogador aceito ({@code requestStatus} = "ACCEPTED"; quem saiu da mesa só aparece se houver histórico, com o
     * status atual do pedido). {@code lastMessage} null = vazia.
     */
    public record ThreadView(String userId, String name, String requestStatus, int unread,
                             LastMessageView lastMessage) {
    }

    /** Visão geral dos chats da partida para quem está vendo. {@code members} vazio se não pode usar a mesa. */
    public record ChatsView(boolean canUseTableChat, int tableUnread, List<MemberView> members,
                            List<ThreadView> threads) {
    }

    /** {@code recipientId} null = mensagem da mesa */
    public record MessageView(String id, String senderId, String senderName, String recipientId, String body,
                              String createdAt, boolean mine) {
    }
}

package com.example.blue_hawk.application.dto.direct;

/** DTOs das mensagens diretas entre usuários. Datas em ISO-8601 UTC. */
public final class DirectConversationDtos {

    private DirectConversationDtos() {
    }

    public record UserView(String id, String name) {
    }

    public record LastMessageView(String body, String senderId, String createdAt) {
    }

    /**
     * Conversa vista por um dos participantes. {@code canSend}: false se a outra pessoa não aceita mensagens
     * diretas (o histórico continua legível). {@code lastMessage} null = conversa vazia. {@code unread}: soma
     * das mensagens agregadas nas notificações não lidas da conversa. {@code lastMessageAt} null = vazia.
     */
    public record ConversationView(String id, UserView otherUser, boolean canSend, LastMessageView lastMessage,
                                   int unread, String createdAt, String lastMessageAt) {
    }

    /** Mesmo formato da mensagem do chat das partidas, sem {@code recipientId} */
    public record MessageView(String id, String senderId, String senderName, String body, String createdAt,
                              boolean mine) {
    }
}

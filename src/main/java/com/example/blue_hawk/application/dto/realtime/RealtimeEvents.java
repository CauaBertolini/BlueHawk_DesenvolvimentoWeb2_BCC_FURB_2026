package com.example.blue_hawk.application.dto.realtime;

/**
 * Eventos enviados pelo WebSocket ({@code /ws}) do servidor para o cliente. Todos têm {@code type}.
 * <ul>
 *   <li>{@code chat.message}: nova mensagem numa conversa, para todos os participantes (inclusive quem enviou,
 *   por causa das outras abas). {@code message} tem o mesmo formato do GET de mensagens da conversa, com
 *   {@code mine} calculado para quem recebe.</li>
 *   <li>{@code notifications.changed}: alguma notificação do usuário foi criada, agregada ou marcada como lida
 *   (só quando alguma linha mudou de fato). Sem payload: o cliente busca de novo.</li>
 *   <li>{@code match.changed}: o estado de uma partida mudou (pedido novo, aceito, recusado ou retirado; partida
 *   cancelada). Vai para o host e para todos com pedido na partida.</li>
 * </ul>
 */
public final class RealtimeEvents {

    public static final String CHAT_MESSAGE = "chat.message";
    public static final String NOTIFICATIONS_CHANGED = "notifications.changed";
    public static final String MATCH_CHANGED = "match.changed";

    private RealtimeEvents() {
    }

    /** {@code channel}: {@link MatchChannel}, {@link NegotiationChannel} ou {@link DirectChannel} */
    public record ChatMessage(String type, Object channel, Object message) {
        public static ChatMessage of(Object channel, Object message) {
            return new ChatMessage(CHAT_MESSAGE, channel, message);
        }
    }

    /**
     * Chat da partida. {@code withUserId} null = mesa; na conversa direta, a OUTRA pessoa do ponto de vista de
     * quem recebe (muda de destinatário para destinatário).
     */
    public record MatchChannel(String kind, String matchId, String withUserId) {
        public static MatchChannel of(String matchId, String withUserId) {
            return new MatchChannel("MATCH", matchId, withUserId);
        }
    }

    public record NegotiationChannel(String kind, String negotiationId) {
        public static NegotiationChannel of(String negotiationId) {
            return new NegotiationChannel("NEGOTIATION", negotiationId);
        }
    }

    public record DirectChannel(String kind, String conversationId) {
        public static DirectChannel of(String conversationId) {
            return new DirectChannel("DIRECT", conversationId);
        }
    }

    public record NotificationsChanged(String type) {
        public static final NotificationsChanged INSTANCE = new NotificationsChanged(NOTIFICATIONS_CHANGED);
    }

    public record MatchChanged(String type, String matchId) {
        public static MatchChanged of(String matchId) {
            return new MatchChanged(MATCH_CHANGED, matchId);
        }
    }
}

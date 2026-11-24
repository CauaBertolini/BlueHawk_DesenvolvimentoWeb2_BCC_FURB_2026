package com.example.blue_hawk.application.dto.chat;

/**
 * DTOs da caixa de conversas (chats das partidas, negociações da Loja e mensagens diretas). Datas em ISO-8601
 * UTC.
 */
public final class ChatInboxDtos {

    private ChatInboxDtos() {
    }

    public record LastMessageView(String body, String createdAt, String senderName, boolean mine) {
    }

    /**
     * Uma conversa da caixa.
     * <ul>
     *   <li>{@code key}: {@code match:{matchId}:table}, {@code match:{matchId}:{otherUserId}},
     *   {@code neg:{negotiationId}} ou {@code dm:{directConversationId}}.</li>
     *   <li>{@code kind}: MATCH_TABLE | MATCH_DIRECT | NEGOTIATION | DIRECT.</li>
     *   <li>{@code title}: nome do jogo; em DIRECT, o nome da outra pessoa ({@code subtitle} = "Mensagem
     *   direta").</li>
     *   <li>{@code withUserId}: a outra pessoa da conversa direta da partida, da negociação (o comprador para o
     *   vendedor, o vendedor para o comprador) ou da mensagem direta; null na mesa.</li>
     *   <li>{@code directConversationId}: só em DIRECT.</li>
     *   <li>{@code status}: status da partida (OPEN/CANCELLED) ou da negociação (OPEN/ACCEPTED/CLOSED); em
     *   DIRECT, OPEN (dá para escrever) ou CLOSED (a outra pessoa não aceita mensagens diretas).</li>
     *   <li>{@code unread}: soma das mensagens agregadas nas notificações não lidas da conversa.</li>
     *   <li>{@code lastActivityAt}: última mensagem, ou a criação da negociação/pedido/partida/conversa.</li>
     *   <li>{@code lastMessage}: null = conversa vazia.</li>
     * </ul>
     */
    public record ChatItemView(String key, String kind, String title, String subtitle, String matchId,
                               String withUserId, String negotiationId, String directConversationId, String status,
                               int unread, String lastActivityAt, LastMessageView lastMessage) {
    }
}

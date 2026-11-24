package com.example.blue_hawk.application.usecase.openmatch;

import com.example.blue_hawk.application.dto.auth.CurrentUser;
import com.example.blue_hawk.application.dto.openmatch.OpenMatchChatDtos.ChatsView;
import com.example.blue_hawk.application.dto.openmatch.OpenMatchChatDtos.MessageView;

import java.util.List;

/**
 * Chat das partidas do Jogue.
 * <ul>
 *   <li>Mesa: o host e os aceitos leem e escrevem.</li>
 *   <li>Conversa direta entre o host e uma pessoa com pedido na partida. Ler exige um pedido (qualquer
 *   status); escrever exige pedido pendente ou aceito.</li>
 *   <li>Conversa direta entre dois jogadores aceitos na mesa. Se um deles deixa de estar aceito, o histórico
 *   segue legível, mas escrever dá 403.</li>
 *   <li>Partida cancelada ou que passou há mais de 7 dias: só leitura (409 ao enviar).</li>
 * </ul>
 */
public interface OpenMatchChatUseCase {

    /** Até quantas mensagens (as mais recentes) uma leitura devolve */
    int MESSAGES_LIMIT = 200;
    int BODY_MAX = 1000;

    /** Visão geral: pode usar a mesa, integrantes, conversas diretas e não lidas */
    ChatsView chats(String openMatchId, CurrentUser currentUser);

    /**
     * Mensagens em ordem cronológica. {@code withUserId} null = mesa. {@code after} (ISO-8601, opcional): só as
     * posteriores. Marca como lidas as notificações dessa conversa.
     */
    List<MessageView> messages(String openMatchId, String withUserId, String after, CurrentUser currentUser);

    /** {@code recipientId} null = mesa */
    MessageView send(String openMatchId, String body, String recipientId, CurrentUser currentUser);
}

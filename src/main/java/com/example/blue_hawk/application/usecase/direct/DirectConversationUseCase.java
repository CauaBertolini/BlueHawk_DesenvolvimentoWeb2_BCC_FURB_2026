package com.example.blue_hawk.application.usecase.direct;

import com.example.blue_hawk.application.dto.auth.CurrentUser;
import com.example.blue_hawk.application.dto.direct.DirectConversationDtos.ConversationView;
import com.example.blue_hawk.application.dto.direct.DirectConversationDtos.MessageView;

import java.util.List;

/**
 * Mensagens diretas entre usuários do site (fora das partidas e das negociações). Uma conversa por par.
 * <ul>
 *   <li>Só os dois participantes leem e escrevem (403 para os outros).</li>
 *   <li>Quem desligou {@code allowDirectMessages} no perfil não pode ser procurado para uma conversa nova nem
 *   receber mensagens novas (403); o histórico continua legível.</li>
 * </ul>
 */
public interface DirectConversationUseCase {

    /** Até quantas mensagens (as mais recentes) uma leitura devolve */
    int MESSAGES_LIMIT = 200;
    int BODY_MAX = 1000;

    /** Resultado do get-or-create: {@code created} = a conversa acabou de ser criada (201) */
    record OpenResult(ConversationView conversation, boolean created) {
    }

    /** Devolve a conversa com {@code userId}, criando se ainda não existe */
    OpenResult open(String userId, CurrentUser currentUser);

    ConversationView get(String conversationId, CurrentUser currentUser);

    /**
     * Mensagens em ordem cronológica. {@code after} (ISO-8601, opcional): só as posteriores. Marca como lidas as
     * notificações DIRECT_MESSAGE da conversa de quem está lendo.
     */
    List<MessageView> messages(String conversationId, String after, CurrentUser currentUser);

    MessageView send(String conversationId, String body, CurrentUser currentUser);
}

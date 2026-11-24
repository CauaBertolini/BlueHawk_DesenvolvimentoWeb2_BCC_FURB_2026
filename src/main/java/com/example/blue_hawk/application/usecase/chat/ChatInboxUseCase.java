package com.example.blue_hawk.application.usecase.chat;

import com.example.blue_hawk.application.dto.auth.CurrentUser;
import com.example.blue_hawk.application.dto.chat.ChatInboxDtos.ChatItemView;

import java.util.List;

/**
 * Caixa de conversas de quem está logado: chats das partidas do Jogue (mesa e conversas diretas), negociações
 * da Loja e mensagens diretas entre usuários, da atividade mais recente para a mais antiga.
 */
public interface ChatInboxUseCase {

    /** Máximo de conversas devolvidas */
    int MAX_ITEMS = 60;
    /** Partidas entram na caixa até 30 dias depois da data marcada */
    int MATCH_DAYS_AFTER = 30;

    List<ChatItemView> inbox(CurrentUser currentUser);

    /**
     * Notificações não lidas de mensagem (MATCH_MESSAGE + OFFER_MESSAGE + DIRECT_MESSAGE; linhas, não a soma de
     * {@code count})
     */
    long unreadCount(CurrentUser currentUser);
}

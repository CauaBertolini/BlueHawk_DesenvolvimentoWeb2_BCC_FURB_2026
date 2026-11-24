package com.example.blue_hawk.presentation.chat;

import com.example.blue_hawk.application.dto.auth.CurrentUser;
import com.example.blue_hawk.application.dto.chat.ChatInboxDtos.ChatItemView;
import com.example.blue_hawk.application.dto.notification.NotificationDtos.CountView;
import com.example.blue_hawk.application.usecase.chat.ChatInboxUseCase;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Caixa de conversas (chats das partidas, negociações da Loja e mensagens diretas). Todas as rotas exigem login. */
@RestController
public class ChatInboxController {

    private final ChatInboxUseCase chatInboxUseCase;

    public ChatInboxController(ChatInboxUseCase chatInboxUseCase) {
        this.chatInboxUseCase = chatInboxUseCase;
    }

    /** Da atividade mais recente para a mais antiga (até 60) */
    @GetMapping("/chats")
    public List<ChatItemView> inbox(CurrentUser currentUser) {
        return chatInboxUseCase.inbox(currentUser);
    }

    /** {@code { count }}: notificações de mensagem não lidas (partidas, negociações e mensagens diretas) */
    @GetMapping("/chats/unread-count")
    public CountView unreadCount(CurrentUser currentUser) {
        return new CountView(chatInboxUseCase.unreadCount(currentUser));
    }
}

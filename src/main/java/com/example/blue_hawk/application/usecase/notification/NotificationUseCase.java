package com.example.blue_hawk.application.usecase.notification;

import com.example.blue_hawk.application.dto.auth.CurrentUser;
import com.example.blue_hawk.application.dto.notification.NotificationDtos.NotificationView;

import java.util.List;

/** Notificações de quem está logado: listar, contar as não lidas e marcar como lidas. */
public interface NotificationUseCase {

    int MAX_PAGE_SIZE = 50;

    /** Da última atividade para a mais antiga. {@code size} limitado a {@link #MAX_PAGE_SIZE}. */
    List<NotificationView> list(int page, int size, CurrentUser currentUser);

    /** Notificações não lidas (linhas, não a soma das mensagens agregadas) */
    long unreadCount(CurrentUser currentUser);

    /** 404 se não existe ou é de outro usuário */
    NotificationView markRead(String id, CurrentUser currentUser);

    /** Devolve quantas foram marcadas */
    int markAllRead(CurrentUser currentUser);
}

package com.example.blue_hawk.presentation.notification;

import com.example.blue_hawk.application.dto.auth.CurrentUser;
import com.example.blue_hawk.application.dto.notification.NotificationDtos.CountView;
import com.example.blue_hawk.application.dto.notification.NotificationDtos.NotificationView;
import com.example.blue_hawk.application.usecase.notification.NotificationUseCase;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Notificações de quem está logado. Todas as rotas exigem login. */
@RestController
public class NotificationController {

    private final NotificationUseCase notificationUseCase;

    public NotificationController(NotificationUseCase notificationUseCase) {
        this.notificationUseCase = notificationUseCase;
    }

    /** Da última atividade para a mais antiga. {@code size} no máximo 50. */
    @GetMapping("/notifications")
    public List<NotificationView> list(@RequestParam(name = "page", defaultValue = "0") int page,
                                       @RequestParam(name = "size", defaultValue = "20") int size,
                                       CurrentUser currentUser) {
        return notificationUseCase.list(page, size, currentUser);
    }

    @GetMapping("/notifications/unread-count")
    public CountView unreadCount(CurrentUser currentUser) {
        return new CountView(notificationUseCase.unreadCount(currentUser));
    }

    @PostMapping("/notifications/{id}/read")
    public NotificationView markRead(@PathVariable String id, CurrentUser currentUser) {
        return notificationUseCase.markRead(id, currentUser);
    }

    @PostMapping("/notifications/read-all")
    public CountView markAllRead(CurrentUser currentUser) {
        return new CountView(notificationUseCase.markAllRead(currentUser));
    }
}

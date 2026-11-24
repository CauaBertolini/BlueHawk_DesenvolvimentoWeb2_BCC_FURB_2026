package com.example.blue_hawk.application.usecase.notification;

import com.example.blue_hawk.application.dto.realtime.RealtimeEvents.NotificationsChanged;
import com.example.blue_hawk.application.port.RealtimePublisher;
import com.example.blue_hawk.domain.entity.notification.Notification;
import com.example.blue_hawk.domain.repository.INotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class NotificationServiceImpl implements NotificationService {

    private final INotificationRepository notificationRepository;
    private final RealtimePublisher realtimePublisher;

    public NotificationServiceImpl(INotificationRepository notificationRepository,
                                   RealtimePublisher realtimePublisher) {
        this.notificationRepository = notificationRepository;
        this.realtimePublisher = realtimePublisher;
    }

    @Override
    @Transactional
    public void notifyMatch(UUID recipientId, Notification.Type type, UUID actorId, UUID openMatchId) {
        if (skip(recipientId, actorId)) return;
        notificationRepository.save(Notification.create(recipientId, type, actorId, openMatchId, null, null));
        changed(recipientId);
    }

    @Override
    @Transactional
    public void notifyBoardgame(UUID recipientId, Notification.Type type, UUID actorId, UUID boardgameId) {
        if (skip(recipientId, actorId)) return;
        notificationRepository.save(Notification.create(recipientId, type, actorId, null, boardgameId, null));
        changed(recipientId);
    }

    @Override
    @Transactional
    public void notifyMessage(UUID recipientId, UUID senderId, UUID openMatchId, UUID threadUserId) {
        if (skip(recipientId, senderId)) return;
        Notification notification = notificationRepository.findUnreadMessage(recipientId, openMatchId, threadUserId)
                .map(existing -> {
                    existing.aggregate(senderId);
                    return existing;
                })
                .orElseGet(() -> Notification.create(recipientId, Notification.Type.MATCH_MESSAGE, senderId,
                        openMatchId, null, threadUserId));
        notificationRepository.save(notification);
        changed(recipientId);
    }

    @Override
    @Transactional
    public int markThreadRead(UUID userId, UUID openMatchId, UUID threadUserId) {
        return changedIfAny(userId,
                notificationRepository.markMessagesRead(userId, openMatchId, threadUserId, Instant.now()));
    }

    @Override
    @Transactional
    public void notifyNegotiation(UUID recipientId, Notification.Type type, UUID actorId, UUID negotiationId) {
        if (skip(recipientId, actorId)) return;
        notificationRepository.save(Notification.createForNegotiation(recipientId, type, actorId, negotiationId));
        changed(recipientId);
    }

    @Override
    @Transactional
    public void notifyOfferMessage(UUID recipientId, UUID senderId, UUID negotiationId) {
        if (skip(recipientId, senderId)) return;
        Notification notification = notificationRepository.findUnreadNegotiationMessage(recipientId, negotiationId)
                .map(existing -> {
                    existing.aggregate(senderId);
                    return existing;
                })
                .orElseGet(() -> Notification.createForNegotiation(recipientId, Notification.Type.OFFER_MESSAGE,
                        senderId, negotiationId));
        notificationRepository.save(notification);
        changed(recipientId);
    }

    @Override
    @Transactional
    public int markNegotiationRead(UUID userId, UUID negotiationId) {
        return changedIfAny(userId,
                notificationRepository.markNegotiationMessagesRead(userId, negotiationId, Instant.now()));
    }

    @Override
    @Transactional
    public void notifyDirectMessage(UUID recipientId, UUID senderId, UUID directConversationId) {
        if (skip(recipientId, senderId)) return;
        Notification notification = notificationRepository.findUnreadDirectMessage(recipientId, directConversationId)
                .map(existing -> {
                    existing.aggregate(senderId);
                    return existing;
                })
                .orElseGet(() -> Notification.createForDirect(recipientId, senderId, directConversationId));
        notificationRepository.save(notification);
        changed(recipientId);
    }

    @Override
    @Transactional
    public int markDirectConversationRead(UUID userId, UUID directConversationId) {
        return changedIfAny(userId,
                notificationRepository.markDirectMessagesRead(userId, directConversationId, Instant.now()));
    }

    /** Avisa (depois do commit) que as notificações do usuário mudaram */
    private void changed(UUID userId) {
        realtimePublisher.publishToUser(userId, NotificationsChanged.INSTANCE);
    }

    /** Marcar como lidas só avisa se alguma linha mudou de fato (ler conversa sem não lidas não gera evento) */
    private int changedIfAny(UUID userId, int updated) {
        if (updated > 0) changed(userId);
        return updated;
    }

    /** Sem destinatário ou o próprio autor do evento */
    private static boolean skip(UUID recipientId, UUID actorId) {
        return recipientId == null || recipientId.equals(actorId);
    }
}

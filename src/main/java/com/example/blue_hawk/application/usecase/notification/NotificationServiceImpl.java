package com.example.blue_hawk.application.usecase.notification;

import com.example.blue_hawk.domain.entity.notification.Notification;
import com.example.blue_hawk.domain.repository.INotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class NotificationServiceImpl implements NotificationService {

    private final INotificationRepository notificationRepository;

    public NotificationServiceImpl(INotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Override
    @Transactional
    public void notifyMatch(UUID recipientId, Notification.Type type, UUID actorId, UUID openMatchId) {
        if (skip(recipientId, actorId)) return;
        notificationRepository.save(Notification.create(recipientId, type, actorId, openMatchId, null, null));
    }

    @Override
    @Transactional
    public void notifyBoardgame(UUID recipientId, Notification.Type type, UUID actorId, UUID boardgameId) {
        if (skip(recipientId, actorId)) return;
        notificationRepository.save(Notification.create(recipientId, type, actorId, null, boardgameId, null));
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
    }

    @Override
    @Transactional
    public int markThreadRead(UUID userId, UUID openMatchId, UUID threadUserId) {
        return notificationRepository.markMessagesRead(userId, openMatchId, threadUserId, Instant.now());
    }

    @Override
    @Transactional
    public void notifyNegotiation(UUID recipientId, Notification.Type type, UUID actorId, UUID negotiationId) {
        if (skip(recipientId, actorId)) return;
        notificationRepository.save(Notification.createForNegotiation(recipientId, type, actorId, negotiationId));
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
    }

    @Override
    @Transactional
    public int markNegotiationRead(UUID userId, UUID negotiationId) {
        return notificationRepository.markNegotiationMessagesRead(userId, negotiationId, Instant.now());
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
    }

    @Override
    @Transactional
    public int markDirectConversationRead(UUID userId, UUID directConversationId) {
        return notificationRepository.markDirectMessagesRead(userId, directConversationId, Instant.now());
    }

    /** Sem destinatário ou o próprio autor do evento */
    private static boolean skip(UUID recipientId, UUID actorId) {
        return recipientId == null || recipientId.equals(actorId);
    }
}

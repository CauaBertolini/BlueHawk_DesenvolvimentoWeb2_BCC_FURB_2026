package com.example.blue_hawk.application.usecase.direct;

import com.example.blue_hawk.application.usecase.auth.Access;

import com.example.blue_hawk.application.dto.auth.CurrentUser;
import com.example.blue_hawk.application.dto.direct.DirectConversationDtos.ConversationView;
import com.example.blue_hawk.application.dto.direct.DirectConversationDtos.LastMessageView;
import com.example.blue_hawk.application.dto.direct.DirectConversationDtos.MessageView;
import com.example.blue_hawk.application.dto.direct.DirectConversationDtos.UserView;
import com.example.blue_hawk.application.dto.realtime.RealtimeEvents.ChatMessage;
import com.example.blue_hawk.application.dto.realtime.RealtimeEvents.DirectChannel;
import com.example.blue_hawk.application.port.RealtimePublisher;
import com.example.blue_hawk.application.usecase.notification.NotificationService;
import com.example.blue_hawk.domain.entity.direct.DirectConversation;
import com.example.blue_hawk.domain.entity.direct.DirectMessage;
import com.example.blue_hawk.domain.entity.notification.Notification;
import com.example.blue_hawk.domain.entity.user.User;
import com.example.blue_hawk.domain.entity.user.UserProfile;
import com.example.blue_hawk.domain.repository.IDirectConversationRepository;
import com.example.blue_hawk.domain.repository.IDirectMessageRepository;
import com.example.blue_hawk.domain.repository.INotificationRepository;
import com.example.blue_hawk.domain.repository.IUserProfileRepository;
import com.example.blue_hawk.domain.repository.IUserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class DirectConversationUseCaseImpl implements DirectConversationUseCase {

    private final IDirectConversationRepository conversationRepository;
    private final IDirectMessageRepository messageRepository;
    private final IUserRepository userRepository;
    private final IUserProfileRepository profileRepository;
    private final INotificationRepository notificationRepository;
    private final NotificationService notificationService;
    private final RealtimePublisher realtimePublisher;

    public DirectConversationUseCaseImpl(IDirectConversationRepository conversationRepository,
                                         IDirectMessageRepository messageRepository,
                                         IUserRepository userRepository,
                                         IUserProfileRepository profileRepository,
                                         INotificationRepository notificationRepository,
                                         NotificationService notificationService,
                                         RealtimePublisher realtimePublisher) {
        this.realtimePublisher = realtimePublisher;
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
        this.notificationRepository = notificationRepository;
        this.notificationService = notificationService;
    }

    @Override
    @Transactional
    public OpenResult open(String userId, CurrentUser currentUser) {
        UUID me = Access.requireUserId(currentUser);
        UUID other = parseId(userId, "userId");
        if (other.equals(me)) {
            throw badRequest("You cannot start a conversation with yourself");
        }
        if (userRepository.findById(other).isEmpty()) {
            throw notFound("User not found");
        }

        Optional<DirectConversation> existing = conversationRepository.findBetween(me, other);
        if (existing.isPresent()) {
            return new OpenResult(view(existing.get(), me, new Names()), false);
        }
        if (!acceptsDirectMessages(other)) {
            throw forbidden("This user does not accept direct messages");
        }
        DirectConversation created = conversationRepository.save(DirectConversation.start(me, other));
        return new OpenResult(view(created, me, new Names()), true);
    }

    @Override
    public ConversationView get(String conversationId, CurrentUser currentUser) {
        UUID me = Access.requireUserId(currentUser);
        DirectConversation conversation = load(conversationId, me);
        return view(conversation, me, new Names());
    }

    @Override
    @Transactional
    public List<MessageView> messages(String conversationId, String after, CurrentUser currentUser) {
        UUID me = Access.requireUserId(currentUser);
        DirectConversation conversation = load(conversationId, me);
        Instant afterInstant = parseOptionalInstant(after);

        List<DirectMessage> messages = messageRepository.find(conversation.getId(), afterInstant, MESSAGES_LIMIT);
        notificationService.markDirectConversationRead(me, conversation.getId());

        Names names = new Names();
        return messages.stream().map(m -> messageView(m, me, names)).toList();
    }

    @Override
    @Transactional
    public MessageView send(String conversationId, String body, CurrentUser currentUser) {
        UUID me = Access.requireUserId(currentUser);
        DirectConversation conversation = load(conversationId, me);
        UUID other = conversation.otherThan(me);
        if (!acceptsDirectMessages(other)) {
            throw forbidden("This user does not accept direct messages");
        }
        String text = body == null ? "" : body.trim();
        if (text.isEmpty() || text.length() > BODY_MAX) {
            throw badRequest("body must have between 1 and " + BODY_MAX + " characters");
        }

        DirectMessage saved = messageRepository.save(DirectMessage.send(conversation.getId(), me, text));
        conversation.messageSent(me, saved.getCreatedAt());
        conversationRepository.save(conversation);
        notificationService.notifyDirectMessage(other, me, conversation.getId());

        // Tempo real: os dois (quem enviou também, por causa das outras abas)
        Names names = new Names();
        DirectChannel channel = DirectChannel.of(conversation.getId().toString());
        for (UUID participant : List.of(me, other)) {
            realtimePublisher.publishToUser(participant, ChatMessage.of(channel, messageView(saved, participant,
                    names)));
        }
        return messageView(saved, me, names);
    }

    // ── regras ──────────────────────────────────────────────────────────────

    /** 404 se não existe; 403 se quem pede não é um dos dois */
    private DirectConversation load(String conversationId, UUID viewer) {
        DirectConversation conversation = conversationRepository.findById(parseId(conversationId, "id"))
                .orElseThrow(() -> notFound("Conversation not found"));
        if (!conversation.isParticipant(viewer)) {
            throw forbidden("You are not part of this conversation");
        }
        return conversation;
    }

    /** Sem linha de perfil = padrão (aceita) */
    private boolean acceptsDirectMessages(UUID userId) {
        return profileRepository.findByUserId(userId)
                .map(UserProfile::isAllowDirectMessages)
                .orElse(true);
    }

    // ── montagem das views ──────────────────────────────────────────────────

    private ConversationView view(DirectConversation c, UUID viewer, Names names) {
        UUID other = c.otherThan(viewer);
        LastMessageView last = messageRepository.findLast(c.getId())
                .map(m -> new LastMessageView(m.getBody(), m.getSenderId().toString(), m.getCreatedAt().toString()))
                .orElse(null);
        int unread = notificationRepository.findUnreadDirectMessage(viewer, c.getId())
                .map(Notification::getCount)
                .orElse(0);
        return new ConversationView(
                c.getId().toString(),
                new UserView(other.toString(), names.of(other)),
                acceptsDirectMessages(other),
                last,
                unread,
                iso(c.getCreatedAt()),
                iso(c.getLastMessageAt()));
    }

    private static MessageView messageView(DirectMessage m, UUID viewer, Names names) {
        return new MessageView(m.getId().toString(), m.getSenderId().toString(), names.of(m.getSenderId()),
                m.getBody(), m.getCreatedAt().toString(), m.getSenderId().equals(viewer));
    }

    private static String iso(Instant instant) {
        return instant != null ? instant.toString() : null;
    }

    /** Cache de nomes por requisição */
    private final class Names {
        private final Map<UUID, String> cache = new HashMap<>();

        String of(UUID userId) {
            return cache.computeIfAbsent(userId,
                    id -> userRepository.findById(id).map(User::getName).orElse("Usuário removido"));
        }
    }

    // ── validações ──────────────────────────────────────────────────────────

    private static UUID parseId(String value, String field) {
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw badRequest(field + " is invalid");
        }
    }

    private static Instant parseOptionalInstant(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return OffsetDateTime.parse(value.trim()).toInstant();
        } catch (DateTimeParseException e) {
            throw badRequest("after must be an ISO-8601 date-time (ex.: 2026-12-05T22:00:00Z)");
        }
    }

    private static ResponseStatusException forbidden(String message) {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, message);
    }

    private static ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}

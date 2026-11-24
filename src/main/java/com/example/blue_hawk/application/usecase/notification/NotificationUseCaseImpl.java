package com.example.blue_hawk.application.usecase.notification;

import com.example.blue_hawk.application.dto.auth.CurrentUser;
import com.example.blue_hawk.application.dto.notification.NotificationDtos.ActorView;
import com.example.blue_hawk.application.dto.notification.NotificationDtos.BoardgameView;
import com.example.blue_hawk.application.dto.notification.NotificationDtos.MatchView;
import com.example.blue_hawk.application.dto.notification.NotificationDtos.NegotiationView;
import com.example.blue_hawk.application.dto.notification.NotificationDtos.NotificationView;
import com.example.blue_hawk.application.dto.realtime.RealtimeEvents.NotificationsChanged;
import com.example.blue_hawk.application.port.RealtimePublisher;
import com.example.blue_hawk.domain.entity.boardgame.Boardgame;
import com.example.blue_hawk.domain.entity.notification.Notification;
import com.example.blue_hawk.domain.entity.openmatch.OpenMatch;
import com.example.blue_hawk.domain.entity.user.User;
import com.example.blue_hawk.domain.repository.IBoardgameRepository;
import com.example.blue_hawk.domain.repository.INotificationRepository;
import com.example.blue_hawk.domain.repository.IOfferNegotiationRepository;
import com.example.blue_hawk.domain.repository.IOfferRepository;
import com.example.blue_hawk.domain.repository.IOpenMatchRepository;
import com.example.blue_hawk.domain.repository.IUserBoardGameRepository;
import com.example.blue_hawk.domain.repository.IUserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class NotificationUseCaseImpl implements NotificationUseCase {

    private final INotificationRepository notificationRepository;
    private final IUserRepository userRepository;
    private final IOpenMatchRepository openMatchRepository;
    private final IUserBoardGameRepository userBoardGameRepository;
    private final IBoardgameRepository boardgameRepository;
    private final IOfferNegotiationRepository negotiationRepository;
    private final IOfferRepository offerRepository;
    private final RealtimePublisher realtimePublisher;

    public NotificationUseCaseImpl(INotificationRepository notificationRepository,
                                   IUserRepository userRepository,
                                   IOpenMatchRepository openMatchRepository,
                                   IUserBoardGameRepository userBoardGameRepository,
                                   IBoardgameRepository boardgameRepository,
                                   IOfferNegotiationRepository negotiationRepository,
                                   IOfferRepository offerRepository,
                                   RealtimePublisher realtimePublisher) {
        this.realtimePublisher = realtimePublisher;
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.openMatchRepository = openMatchRepository;
        this.userBoardGameRepository = userBoardGameRepository;
        this.boardgameRepository = boardgameRepository;
        this.negotiationRepository = negotiationRepository;
        this.offerRepository = offerRepository;
    }

    @Override
    public List<NotificationView> list(int page, int size, CurrentUser currentUser) {
        UUID userId = requireLogin(currentUser);
        int safePage = Math.max(0, page);
        int safeSize = Math.min(MAX_PAGE_SIZE, Math.max(1, size));
        Lookups lookups = new Lookups();
        return notificationRepository.findByUser(userId, safePage, safeSize).stream()
                .map(n -> view(n, lookups))
                .toList();
    }

    @Override
    public long unreadCount(CurrentUser currentUser) {
        return notificationRepository.countUnread(requireLogin(currentUser));
    }

    @Override
    @Transactional
    public NotificationView markRead(String id, CurrentUser currentUser) {
        UUID userId = requireLogin(currentUser);
        Notification notification = parseOptionalId(id)
                .flatMap(notificationRepository::findById)
                .filter(n -> userId.equals(n.getUserId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
        if (!notification.isRead()) {
            notification.markRead();
            notification = notificationRepository.save(notification);
            realtimePublisher.publishToUser(userId, NotificationsChanged.INSTANCE);
        }
        return view(notification, new Lookups());
    }

    @Override
    @Transactional
    public int markAllRead(CurrentUser currentUser) {
        UUID userId = requireLogin(currentUser);
        int updated = notificationRepository.markAllRead(userId, Instant.now());
        if (updated > 0) realtimePublisher.publishToUser(userId, NotificationsChanged.INSTANCE);
        return updated;
    }

    // ── montagem das views ──────────────────────────────────────────────────

    /** Cache por requisição: várias notificações costumam apontar para a mesma pessoa/partida/jogo */
    private final class Lookups {
        private final Map<UUID, Optional<ActorView>> actors = new HashMap<>();
        private final Map<UUID, Optional<MatchView>> matches = new HashMap<>();
        private final Map<UUID, Optional<BoardgameView>> boardgames = new HashMap<>();
        private final Map<UUID, Optional<NegotiationView>> negotiations = new HashMap<>();

        NegotiationView negotiation(UUID id) {
            if (id == null) return null;
            return negotiations.computeIfAbsent(id, key -> negotiationRepository.findById(key)
                    .map(n -> new NegotiationView(n.getId().toString(), offerGameName(n.getOfferId()),
                            n.getOfferId().toString()))).orElse(null);
        }

        private String offerGameName(UUID offerId) {
            return offerRepository.findById(offerId)
                    .flatMap(offer -> userBoardGameRepository.findById(offer.getUserBoardgameId()))
                    .flatMap(copy -> boardgameRepository.findById(copy.getBoardgameId()))
                    .map(Boardgame::getName)
                    .orElse(null);
        }

        ActorView actor(UUID id) {
            if (id == null) return null;
            return actors.computeIfAbsent(id, key -> userRepository.findById(key)
                    .map(User::getName)
                    .map(name -> new ActorView(key.toString(), name))).orElse(null);
        }

        MatchView match(UUID id) {
            if (id == null) return null;
            return matches.computeIfAbsent(id, key -> openMatchRepository.findById(key)
                    .map(m -> new MatchView(m.getId().toString(), gameName(m), m.getScheduledAt().toString(),
                            m.getCancelReason())))
                    .orElse(null);
        }

        BoardgameView boardgame(UUID id) {
            if (id == null) return null;
            return boardgames.computeIfAbsent(id, key -> boardgameRepository.findById(key)
                    .map(g -> new BoardgameView(g.getId().toString(), g.getName()))).orElse(null);
        }

        private String gameName(OpenMatch match) {
            return userBoardGameRepository.findById(match.getUserBoardgameId())
                    .flatMap(copy -> boardgameRepository.findById(copy.getBoardgameId()))
                    .map(Boardgame::getName)
                    .orElse(null);
        }
    }

    private NotificationView view(Notification n, Lookups lookups) {
        return new NotificationView(
                n.getId().toString(),
                n.getType().name(),
                n.isRead(),
                n.lastActivityAt().toString(),
                n.getCount(),
                lookups.actor(n.getActorId()),
                lookups.match(n.getOpenMatchId()),
                lookups.boardgame(n.getBoardgameId()),
                n.getThreadUserId() != null ? n.getThreadUserId().toString() : null,
                lookups.negotiation(n.getNegotiationId()),
                n.getDirectConversationId() != null ? n.getDirectConversationId().toString() : null);
    }

    // ── validações ──────────────────────────────────────────────────────────

    private static UUID requireLogin(CurrentUser currentUser) {
        if (currentUser == null || !currentUser.authenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return currentUser.userId();
    }

    /** Id inválido é tratado como inexistente (404) */
    private static Optional<UUID> parseOptionalId(String value) {
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException | NullPointerException e) {
            return Optional.empty();
        }
    }
}

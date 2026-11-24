package com.example.blue_hawk.application.usecase.chat;

import com.example.blue_hawk.application.dto.auth.CurrentUser;
import com.example.blue_hawk.application.dto.chat.ChatInboxDtos.ChatItemView;
import com.example.blue_hawk.application.dto.chat.ChatInboxDtos.LastMessageView;
import com.example.blue_hawk.domain.entity.boardgame.Boardgame;
import com.example.blue_hawk.domain.entity.direct.DirectConversation;
import com.example.blue_hawk.domain.entity.direct.DirectMessage;
import com.example.blue_hawk.domain.entity.negotiation.OfferNegotiation;
import com.example.blue_hawk.domain.entity.negotiation.OfferNegotiationMessage;
import com.example.blue_hawk.domain.entity.notification.Notification;
import com.example.blue_hawk.domain.entity.offer.Offer;
import com.example.blue_hawk.domain.entity.openmatch.OpenMatch;
import com.example.blue_hawk.domain.entity.openmatch.OpenMatchMessage;
import com.example.blue_hawk.domain.entity.openmatch.OpenMatchRequest;
import com.example.blue_hawk.domain.entity.user.User;
import com.example.blue_hawk.domain.entity.user.UserProfile;
import com.example.blue_hawk.domain.entity.userboardgame.UserBoardgame;
import com.example.blue_hawk.domain.repository.IBoardgameRepository;
import com.example.blue_hawk.domain.repository.IDirectConversationRepository;
import com.example.blue_hawk.domain.repository.IDirectMessageRepository;
import com.example.blue_hawk.domain.repository.INotificationRepository;
import com.example.blue_hawk.domain.repository.IOfferNegotiationMessageRepository;
import com.example.blue_hawk.domain.repository.IOfferNegotiationRepository;
import com.example.blue_hawk.domain.repository.IOfferRepository;
import com.example.blue_hawk.domain.repository.IOpenMatchMessageRepository;
import com.example.blue_hawk.domain.repository.IOpenMatchRepository;
import com.example.blue_hawk.domain.repository.IUserBoardGameRepository;
import com.example.blue_hawk.domain.repository.IUserProfileRepository;
import com.example.blue_hawk.domain.repository.IUserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class ChatInboxUseCaseImpl implements ChatInboxUseCase {

    static final String MATCH_TABLE = "MATCH_TABLE";
    static final String MATCH_DIRECT = "MATCH_DIRECT";
    static final String NEGOTIATION = "NEGOTIATION";
    static final String DIRECT = "DIRECT";

    private final IOpenMatchRepository openMatchRepository;
    private final IOpenMatchMessageRepository matchMessageRepository;
    private final IOfferNegotiationRepository negotiationRepository;
    private final IOfferNegotiationMessageRepository negotiationMessageRepository;
    private final IOfferRepository offerRepository;
    private final IUserBoardGameRepository userBoardGameRepository;
    private final IBoardgameRepository boardgameRepository;
    private final IUserRepository userRepository;
    private final INotificationRepository notificationRepository;
    private final IDirectConversationRepository directConversationRepository;
    private final IDirectMessageRepository directMessageRepository;
    private final IUserProfileRepository profileRepository;

    public ChatInboxUseCaseImpl(IOpenMatchRepository openMatchRepository,
                                IOpenMatchMessageRepository matchMessageRepository,
                                IOfferNegotiationRepository negotiationRepository,
                                IOfferNegotiationMessageRepository negotiationMessageRepository,
                                IOfferRepository offerRepository,
                                IUserBoardGameRepository userBoardGameRepository,
                                IBoardgameRepository boardgameRepository,
                                IUserRepository userRepository,
                                INotificationRepository notificationRepository,
                                IDirectConversationRepository directConversationRepository,
                                IDirectMessageRepository directMessageRepository,
                                IUserProfileRepository profileRepository) {
        this.directConversationRepository = directConversationRepository;
        this.directMessageRepository = directMessageRepository;
        this.profileRepository = profileRepository;
        this.openMatchRepository = openMatchRepository;
        this.matchMessageRepository = matchMessageRepository;
        this.negotiationRepository = negotiationRepository;
        this.negotiationMessageRepository = negotiationMessageRepository;
        this.offerRepository = offerRepository;
        this.userBoardGameRepository = userBoardGameRepository;
        this.boardgameRepository = boardgameRepository;
        this.userRepository = userRepository;
        this.notificationRepository = notificationRepository;
    }

    /** Item com o instante usado na ordenação */
    private record Ranked(ChatItemView view, Instant activity) {
    }

    @Override
    public List<ChatItemView> inbox(CurrentUser currentUser) {
        UUID me = requireLogin(currentUser);
        List<Notification> unread = notificationRepository.findUnreadChatMessages(me);
        Lookups lookups = new Lookups();

        List<Ranked> items = new ArrayList<>();
        Instant from = Instant.now().minus(Duration.ofDays(MATCH_DAYS_AFTER));
        for (OpenMatch match : openMatchRepository.findInvolvingSince(me, from)) {
            addMatchItems(match, me, unread, lookups, items);
        }
        for (OfferNegotiation negotiation : negotiationRepository.findInvolving(me)) {
            negotiationItem(negotiation, me, unread, lookups).ifPresent(items::add);
        }
        for (DirectConversation conversation : directConversationRepository.findInvolving(me)) {
            directConversationItem(conversation, me, unread, lookups).ifPresent(items::add);
        }

        return items.stream()
                .sorted(Comparator.comparing(Ranked::activity).reversed()
                        .thenComparing(r -> r.view().key()))
                .limit(MAX_ITEMS)
                .map(Ranked::view)
                .toList();
    }

    @Override
    public long unreadCount(CurrentUser currentUser) {
        return notificationRepository.countUnreadChatMessages(requireLogin(currentUser));
    }

    // ── partidas ────────────────────────────────────────────────────────────

    /**
     * Mesa: host ou aceito. Conversas diretas: para o host, uma por pessoa que pediu e já tem mensagem; para quem
     * pediu, a conversa com o host (mesmo vazia, enquanto o pedido está pendente ou aceito) e as conversas com
     * outros jogadores que já têm mensagem.
     */
    private void addMatchItems(OpenMatch match, UUID me, List<Notification> unread, Lookups lookups,
                               List<Ranked> items) {
        String title = lookups.gameOfCopy(match.getUserBoardgameId());
        String status = match.getStatus().name();
        String matchId = match.getId().toString();

        if (match.isHostedBy(me)) {
            items.add(tableItem(match, me, title, unread, lookups));
            for (OpenMatchRequest request : openMatchRepository.findRequests(match.getId())) {
                UUID other = request.getUserId();
                matchMessageRepository.findLastDirect(match.getId(), me, other).ifPresent(last ->
                        items.add(directItem(matchId, other, title, status, last, request.getCreatedAt(), me,
                                matchUnread(unread, match.getId(), other), lookups)));
            }
            return;
        }

        Optional<OpenMatchRequest> mine = openMatchRepository.findRequest(match.getId(), me);
        if (mine.isEmpty()) return;
        OpenMatchRequest request = mine.get();
        if (request.getStatus() == OpenMatchRequest.Status.ACCEPTED) {
            items.add(tableItem(match, me, title, unread, lookups));
        }
        UUID host = match.getHostId();
        OpenMatchMessage last = matchMessageRepository.findLastDirect(match.getId(), me, host).orElse(null);
        if (last != null || request.isActive()) {
            items.add(directItem(matchId, host, title, status, last, request.getCreatedAt(), me,
                    matchUnread(unread, match.getId(), host), lookups));
        }
        // Conversas diretas com outros jogadores da mesa: só as que já têm mensagem (como as do host)
        for (OpenMatchRequest other : openMatchRepository.findRequests(match.getId())) {
            UUID otherId = other.getUserId();
            if (otherId.equals(me) || otherId.equals(host)) continue;
            matchMessageRepository.findLastDirect(match.getId(), me, otherId).ifPresent(lastWith ->
                    items.add(directItem(matchId, otherId, title, status, lastWith, other.getCreatedAt(), me,
                            matchUnread(unread, match.getId(), otherId), lookups)));
        }
    }

    private Ranked tableItem(OpenMatch match, UUID me, String title, List<Notification> unread, Lookups lookups) {
        OpenMatchMessage last = matchMessageRepository.findLastTable(match.getId()).orElse(null);
        Instant activity = last != null ? last.getCreatedAt() : match.getCreatedAt();
        return new Ranked(new ChatItemView(
                "match:" + match.getId() + ":table",
                MATCH_TABLE,
                title,
                "Mesa · " + match.getCity(),
                match.getId().toString(),
                null,
                null,
                null,
                match.getStatus().name(),
                matchUnread(unread, match.getId(), null),
                iso(activity),
                last != null ? lastMessage(last.getBody(), last.getCreatedAt(), last.getSenderId(), me, lookups)
                        : null), orEpoch(activity));
    }

    private Ranked directItem(String matchId, UUID other, String title, String status, OpenMatchMessage last,
                              Instant requestCreatedAt, UUID me, int unread, Lookups lookups) {
        Instant activity = last != null ? last.getCreatedAt() : requestCreatedAt;
        return new Ranked(new ChatItemView(
                "match:" + matchId + ":" + other,
                MATCH_DIRECT,
                title,
                lookups.name(other),
                matchId,
                other.toString(),
                null,
                null,
                status,
                unread,
                iso(activity),
                last != null ? lastMessage(last.getBody(), last.getCreatedAt(), last.getSenderId(), me, lookups)
                        : null), orEpoch(activity));
    }

    /** Mesma semântica do chat da partida: {@code threadUserId} null = mesa, senão a outra pessoa */
    private static int matchUnread(List<Notification> unread, UUID matchId, UUID threadUserId) {
        return unread.stream()
                .filter(n -> n.getType() == Notification.Type.MATCH_MESSAGE)
                .filter(n -> matchId.equals(n.getOpenMatchId()))
                .filter(n -> Objects.equals(n.getThreadUserId(), threadUserId))
                .mapToInt(Notification::getCount)
                .sum();
    }

    // ── negociações ─────────────────────────────────────────────────────────

    private Optional<Ranked> negotiationItem(OfferNegotiation negotiation, UUID me, List<Notification> unread,
                                             Lookups lookups) {
        Offer offer = offerRepository.findById(negotiation.getOfferId()).orElse(null);
        if (offer == null) return Optional.empty();
        UserBoardgame copy = lookups.copy(offer.getUserBoardgameId());
        if (copy == null) return Optional.empty();

        boolean isSeller = me.equals(copy.getUserId());
        String subtitle = isSeller
                ? "Vendendo para " + lookups.name(negotiation.getBuyerId())
                : "Comprando de " + lookups.name(copy.getUserId());
        OfferNegotiationMessage last = negotiationMessageRepository.findLast(negotiation.getId()).orElse(null);
        Instant activity = last != null ? last.getCreatedAt() : negotiation.getCreatedAt();
        int unreadCount = unread.stream()
                .filter(n -> n.getType() == Notification.Type.OFFER_MESSAGE)
                .filter(n -> negotiation.getId().equals(n.getNegotiationId()))
                .mapToInt(Notification::getCount)
                .sum();

        return Optional.of(new Ranked(new ChatItemView(
                "neg:" + negotiation.getId(),
                NEGOTIATION,
                lookups.gameOfCopy(copy.getId()),
                subtitle,
                null,
                // A outra parte: o comprador para quem vende, o vendedor para quem compra
                (isSeller ? negotiation.getBuyerId() : copy.getUserId()).toString(),
                negotiation.getId().toString(),
                null,
                negotiation.getStatus().name(),
                unreadCount,
                iso(activity),
                last != null ? lastMessage(last.getBody(), last.getCreatedAt(), last.getSenderId(), me, lookups)
                        : null), orEpoch(activity)));
    }

    // ── mensagens diretas ───────────────────────────────────────────────────

    /**
     * Conversa direta entre usuários. Vazia, só aparece para quem a abriu (a outra pessoa só a vê quando chega a
     * primeira mensagem).
     */
    private Optional<Ranked> directConversationItem(DirectConversation conversation, UUID me,
                                                    List<Notification> unread, Lookups lookups) {
        DirectMessage last = directMessageRepository.findLast(conversation.getId()).orElse(null);
        if (last == null && !me.equals(conversation.getCreatedBy())) return Optional.empty();

        UUID other = conversation.otherThan(me);
        boolean canSend = profileRepository.findByUserId(other)
                .map(UserProfile::isAllowDirectMessages)
                .orElse(true);
        Instant activity = last != null ? last.getCreatedAt() : conversation.getCreatedAt();
        int unreadCount = unread.stream()
                .filter(n -> n.getType() == Notification.Type.DIRECT_MESSAGE)
                .filter(n -> conversation.getId().equals(n.getDirectConversationId()))
                .mapToInt(Notification::getCount)
                .sum();

        return Optional.of(new Ranked(new ChatItemView(
                "dm:" + conversation.getId(),
                DIRECT,
                lookups.name(other),
                "Mensagem direta",
                null,
                other.toString(),
                null,
                conversation.getId().toString(),
                canSend ? "OPEN" : "CLOSED",
                unreadCount,
                iso(activity),
                last != null ? lastMessage(last.getBody(), last.getCreatedAt(), last.getSenderId(), me, lookups)
                        : null), orEpoch(activity)));
    }

    // ── montagem ────────────────────────────────────────────────────────────

    private static LastMessageView lastMessage(String body, Instant createdAt, UUID senderId, UUID me,
                                               Lookups lookups) {
        return new LastMessageView(body, iso(createdAt), lookups.name(senderId), senderId.equals(me));
    }

    private static String iso(Instant instant) {
        return instant != null ? instant.toString() : null;
    }

    private static Instant orEpoch(Instant instant) {
        return instant != null ? instant : Instant.EPOCH;
    }

    /** Caches da requisição: nomes, cópias e jogos se repetem entre as conversas */
    private final class Lookups {
        private final Map<UUID, String> names = new HashMap<>();
        private final Map<UUID, Optional<UserBoardgame>> copies = new HashMap<>();
        private final Map<UUID, Optional<Boardgame>> games = new HashMap<>();

        String name(UUID userId) {
            return names.computeIfAbsent(userId,
                    id -> userRepository.findById(id).map(User::getName).orElse("Usuário removido"));
        }

        UserBoardgame copy(UUID userBoardgameId) {
            return copies.computeIfAbsent(userBoardgameId, userBoardGameRepository::findById).orElse(null);
        }

        String gameOfCopy(UUID userBoardgameId) {
            UserBoardgame copy = copy(userBoardgameId);
            if (copy == null) return null;
            return games.computeIfAbsent(copy.getBoardgameId(), boardgameRepository::findById)
                    .map(Boardgame::getName)
                    .orElse(null);
        }
    }

    private static UUID requireLogin(CurrentUser currentUser) {
        if (currentUser == null || !currentUser.authenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return currentUser.userId();
    }
}

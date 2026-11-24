package com.example.blue_hawk.application.usecase.openmatch;

import com.example.blue_hawk.application.dto.auth.CurrentUser;
import com.example.blue_hawk.application.dto.openmatch.OpenMatchChatDtos.ChatsView;
import com.example.blue_hawk.application.dto.openmatch.OpenMatchChatDtos.LastMessageView;
import com.example.blue_hawk.application.dto.openmatch.OpenMatchChatDtos.MemberView;
import com.example.blue_hawk.application.dto.openmatch.OpenMatchChatDtos.MessageView;
import com.example.blue_hawk.application.dto.openmatch.OpenMatchChatDtos.ThreadView;
import com.example.blue_hawk.application.dto.realtime.RealtimeEvents.ChatMessage;
import com.example.blue_hawk.application.dto.realtime.RealtimeEvents.MatchChannel;
import com.example.blue_hawk.application.port.RealtimePublisher;
import com.example.blue_hawk.application.usecase.notification.NotificationService;
import com.example.blue_hawk.domain.entity.notification.Notification;
import com.example.blue_hawk.domain.entity.openmatch.OpenMatch;
import com.example.blue_hawk.domain.entity.openmatch.OpenMatchMessage;
import com.example.blue_hawk.domain.entity.openmatch.OpenMatchRequest;
import com.example.blue_hawk.domain.entity.user.User;
import com.example.blue_hawk.domain.repository.INotificationRepository;
import com.example.blue_hawk.domain.repository.IOpenMatchMessageRepository;
import com.example.blue_hawk.domain.repository.IOpenMatchRepository;
import com.example.blue_hawk.domain.repository.IUserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

@Service
public class OpenMatchChatUseCaseImpl implements OpenMatchChatUseCase {

    /** Depois da data da partida, o chat segue aberto para escrita por este tempo */
    static final Duration WRITABLE_AFTER_MATCH = Duration.ofDays(7);
    /** {@code requestStatus} da conversa do participante com o host */
    static final String HOST_THREAD = "HOST";

    private final IOpenMatchRepository openMatchRepository;
    private final IOpenMatchMessageRepository messageRepository;
    private final INotificationRepository notificationRepository;
    private final NotificationService notificationService;
    private final IUserRepository userRepository;
    private final RealtimePublisher realtimePublisher;

    public OpenMatchChatUseCaseImpl(IOpenMatchRepository openMatchRepository,
                                    IOpenMatchMessageRepository messageRepository,
                                    INotificationRepository notificationRepository,
                                    NotificationService notificationService,
                                    IUserRepository userRepository,
                                    RealtimePublisher realtimePublisher) {
        this.realtimePublisher = realtimePublisher;
        this.openMatchRepository = openMatchRepository;
        this.messageRepository = messageRepository;
        this.notificationRepository = notificationRepository;
        this.notificationService = notificationService;
        this.userRepository = userRepository;
    }

    @Override
    public ChatsView chats(String openMatchId, CurrentUser currentUser) {
        UUID viewer = requireLogin(currentUser);
        OpenMatch match = findMatch(openMatchId);
        boolean isHost = match.isHostedBy(viewer);
        Optional<OpenMatchRequest> myRequest = isHost ? Optional.empty()
                : openMatchRepository.findRequest(match.getId(), viewer);
        if (!isHost && myRequest.isEmpty()) {
            throw forbidden("You are not part of this match");
        }
        boolean canUseTable = isHost || myRequest.get().getStatus() == OpenMatchRequest.Status.ACCEPTED;

        List<Notification> unread = notificationRepository.findUnreadMessages(viewer, match.getId());
        int tableUnread = unreadIn(unread, null);
        Names names = new Names();

        List<OpenMatchRequest> requests = openMatchRepository.findRequests(match.getId());
        List<MemberView> members = new ArrayList<>();
        if (canUseTable) {
            members.add(new MemberView(match.getHostId().toString(), names.of(match.getHostId()), true));
            requests.stream()
                    .filter(r -> r.getStatus() == OpenMatchRequest.Status.ACCEPTED)
                    .forEach(r -> members.add(new MemberView(r.getUserId().toString(), names.of(r.getUserId()),
                            false)));
        }

        List<ThreadView> threads;
        if (isHost) {
            threads = requests.stream()
                    .map(r -> {
                        Optional<OpenMatchMessage> last = messageRepository.findLastDirect(match.getId(), viewer,
                                r.getUserId());
                        Instant activity = Stream.of(last.map(OpenMatchMessage::getCreatedAt).orElse(null),
                                        r.getCreatedAt(), r.getDecidedAt())
                                .filter(Objects::nonNull)
                                .max(Comparator.naturalOrder())
                                .orElse(Instant.EPOCH);
                        return new Ranked(new ThreadView(r.getUserId().toString(), names.of(r.getUserId()),
                                r.getStatus().name(), unreadIn(unread, r.getUserId()),
                                last.map(OpenMatchChatUseCaseImpl::lastMessageView).orElse(null)), activity);
                    })
                    .sorted(Comparator.comparing(Ranked::activity).reversed())
                    .map(Ranked::view)
                    .toList();
        } else {
            threads = participantThreads(match, viewer, canUseTable, requests, unread, names);
        }

        return new ChatsView(canUseTable, tableUnread, members, threads);
    }

    @Override
    @Transactional
    public List<MessageView> messages(String openMatchId, String withUserId, String after, CurrentUser currentUser) {
        UUID viewer = requireLogin(currentUser);
        OpenMatch match = findMatch(openMatchId);
        UUID with = parseOptionalId(withUserId, "with");
        Instant afterInstant = parseOptionalInstant(after);
        requireAccess(match, viewer, with, false);

        List<OpenMatchMessage> messages = with == null
                ? messageRepository.findTable(match.getId(), afterInstant, MESSAGES_LIMIT)
                : messageRepository.findDirect(match.getId(), viewer, with, afterInstant, MESSAGES_LIMIT);
        notificationService.markThreadRead(viewer, match.getId(), with);

        Names names = new Names();
        return messages.stream().map(m -> messageView(m, viewer, names)).toList();
    }

    @Override
    @Transactional
    public MessageView send(String openMatchId, String body, String recipientId, CurrentUser currentUser) {
        UUID sender = requireLogin(currentUser);
        OpenMatch match = findMatch(openMatchId);
        UUID recipient = parseOptionalId(recipientId, "recipientId");
        requireAccess(match, sender, recipient, true);
        if (isReadOnly(match, Instant.now())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This chat is read-only");
        }
        String text = body == null ? "" : body.trim();
        if (text.isEmpty() || text.length() > BODY_MAX) {
            throw badRequest("body must have between 1 and " + BODY_MAX + " characters");
        }

        OpenMatchMessage saved = messageRepository.save(OpenMatchMessage.send(match.getId(), sender, recipient, text));

        Names names = new Names();
        String matchId = match.getId().toString();
        if (recipient == null) {
            // Mesa: o host e todos os aceitos; notificação para todos menos quem enviou
            Set<UUID> table = new LinkedHashSet<>();
            table.add(match.getHostId());
            openMatchRepository.findRequests(match.getId()).stream()
                    .filter(r -> r.getStatus() == OpenMatchRequest.Status.ACCEPTED)
                    .forEach(r -> table.add(r.getUserId()));
            table.stream()
                    .filter(r -> !r.equals(sender))
                    .forEach(r -> notificationService.notifyMessage(r, sender, match.getId(), null));
            // Tempo real: todos da mesa, inclusive quem enviou (outras abas)
            table.add(sender);
            table.forEach(r -> realtimePublisher.publishToUser(r, ChatMessage.of(MatchChannel.of(matchId, null),
                    messageView(saved, r, names))));
        } else {
            notificationService.notifyMessage(recipient, sender, match.getId(), sender);
            // Conversa direta: cada um vê a OUTRA pessoa em withUserId
            realtimePublisher.publishToUser(sender, ChatMessage.of(MatchChannel.of(matchId, recipient.toString()),
                    messageView(saved, sender, names)));
            realtimePublisher.publishToUser(recipient, ChatMessage.of(MatchChannel.of(matchId, sender.toString()),
                    messageView(saved, recipient, names)));
        }

        return messageView(saved, sender, names);
    }

    /** Conversa com o instante usado na ordenação */
    private record Ranked(ThreadView view, Instant activity) {
    }

    /**
     * Conversas de quem pediu para entrar: a do host e uma por outro jogador aceito (se quem está vendo também
     * está aceito). Jogadores que saíram da mesa (ou quem está vendo, se saiu) continuam aparecendo enquanto
     * houver histórico, para a conversa seguir legível. Ordem: última mensagem, da mais recente para a mais
     * antiga; as vazias vão para o fim, com o host na frente.
     */
    private List<ThreadView> participantThreads(OpenMatch match, UUID viewer, boolean viewerAccepted,
                                                List<OpenMatchRequest> requests, List<Notification> unread,
                                                Names names) {
        UUID host = match.getHostId();
        List<Ranked> ranked = new ArrayList<>();
        Optional<OpenMatchMessage> hostLast = messageRepository.findLastDirect(match.getId(), viewer, host);
        ranked.add(new Ranked(new ThreadView(host.toString(), names.of(host), HOST_THREAD, unreadIn(unread, host),
                hostLast.map(OpenMatchChatUseCaseImpl::lastMessageView).orElse(null)),
                hostLast.map(OpenMatchMessage::getCreatedAt).orElse(null)));

        for (OpenMatchRequest r : requests) {
            UUID other = r.getUserId();
            if (other.equals(viewer) || other.equals(host)) continue;
            boolean bothAccepted = viewerAccepted && r.getStatus() == OpenMatchRequest.Status.ACCEPTED;
            Optional<OpenMatchMessage> last = messageRepository.findLastDirect(match.getId(), viewer, other);
            if (!bothAccepted && last.isEmpty()) continue;
            ranked.add(new Ranked(new ThreadView(other.toString(), names.of(other), r.getStatus().name(),
                    unreadIn(unread, other), last.map(OpenMatchChatUseCaseImpl::lastMessageView).orElse(null)),
                    last.map(OpenMatchMessage::getCreatedAt).orElse(null)));
        }

        // Ordenação estável: entre as vazias fica o host primeiro e depois a ordem dos pedidos
        return ranked.stream()
                .sorted(Comparator.comparing(Ranked::activity, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(Ranked::view)
                .toList();
    }

    // ── regras de acesso ────────────────────────────────────────────────────

    /**
     * {@code with} null = mesa (host ou aceito). Conversa direta:
     * <ul>
     *   <li>host ↔ alguém com pedido na partida: ler exige o pedido (qualquer status); escrever, pedido pendente
     *   ou aceito;</li>
     *   <li>jogador ↔ jogador: escrever exige os dois aceitos; ler também vale enquanto houver histórico entre
     *   eles (alguém saiu da mesa, mas a conversa segue legível).</li>
     * </ul>
     */
    private void requireAccess(OpenMatch match, UUID viewer, UUID with, boolean write) {
        boolean isHost = match.isHostedBy(viewer);
        if (with == null) {
            if (isHost) return;
            boolean accepted = openMatchRepository.findRequest(match.getId(), viewer)
                    .map(r -> r.getStatus() == OpenMatchRequest.Status.ACCEPTED)
                    .orElse(false);
            if (!accepted) throw forbidden("Only the host and accepted players can use the table chat");
            return;
        }
        if (with.equals(viewer)) throw forbidden("You cannot chat with yourself");

        if (isHost || match.isHostedBy(with)) {
            UUID participant = isHost ? with : viewer;
            OpenMatchRequest request = openMatchRepository.findRequest(match.getId(), participant)
                    .orElseThrow(() -> forbidden("There is no request from this user in the match"));
            if (write && !request.isActive()) {
                throw forbidden("This conversation is closed");
            }
            return;
        }

        // Jogador ↔ jogador
        OpenMatchRequest mine = openMatchRepository.findRequest(match.getId(), viewer)
                .orElseThrow(() -> forbidden("You are not part of this match"));
        Optional<OpenMatchRequest> theirs = openMatchRepository.findRequest(match.getId(), with);
        boolean bothAccepted = mine.getStatus() == OpenMatchRequest.Status.ACCEPTED
                && theirs.map(r -> r.getStatus() == OpenMatchRequest.Status.ACCEPTED).orElse(false);
        if (bothAccepted) return;
        if (!write && theirs.isPresent()
                && messageRepository.findLastDirect(match.getId(), viewer, with).isPresent()) {
            return;
        }
        throw forbidden("You can only chat directly with the host or with other accepted players");
    }

    /** Cancelada ou realizada há mais de 7 dias */
    static boolean isReadOnly(OpenMatch match, Instant now) {
        return match.getStatus() == OpenMatch.Status.CANCELLED
                || match.getScheduledAt().plus(WRITABLE_AFTER_MATCH).isBefore(now);
    }

    // ── montagem das views ──────────────────────────────────────────────────

    /** Soma das mensagens agregadas nas notificações não lidas da conversa ({@code threadUserId} null = mesa) */
    private static int unreadIn(List<Notification> unread, UUID threadUserId) {
        return unread.stream()
                .filter(n -> Objects.equals(n.getThreadUserId(), threadUserId))
                .mapToInt(Notification::getCount)
                .sum();
    }

    private static LastMessageView lastMessageView(OpenMatchMessage m) {
        return new LastMessageView(m.getBody(), m.getCreatedAt().toString(), m.getSenderId().toString());
    }

    private static MessageView messageView(OpenMatchMessage m, UUID viewer, Names names) {
        return new MessageView(m.getId().toString(), m.getSenderId().toString(), names.of(m.getSenderId()),
                m.getRecipientId() != null ? m.getRecipientId().toString() : null, m.getBody(),
                m.getCreatedAt().toString(), m.getSenderId().equals(viewer));
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

    private OpenMatch findMatch(String id) {
        UUID matchId;
        try {
            matchId = UUID.fromString(id);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw badRequest("id is invalid");
        }
        return openMatchRepository.findById(matchId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Match not found"));
    }

    private static UUID requireLogin(CurrentUser currentUser) {
        if (currentUser == null || !currentUser.authenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return currentUser.userId();
    }

    private static UUID parseOptionalId(String value, String field) {
        if (value == null || value.isBlank()) return null;
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException e) {
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

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}

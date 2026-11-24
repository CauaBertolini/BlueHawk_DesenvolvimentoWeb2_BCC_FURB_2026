package com.example.blue_hawk.application.usecase.openmatch;

import com.example.blue_hawk.application.dto.auth.CurrentUser;
import com.example.blue_hawk.application.dto.openmatch.OpenMatchDtos.GameView;
import com.example.blue_hawk.application.dto.openmatch.OpenMatchDtos.HostCommand;
import com.example.blue_hawk.application.dto.openmatch.OpenMatchDtos.HostView;
import com.example.blue_hawk.application.dto.openmatch.OpenMatchDtos.ListQuery;
import com.example.blue_hawk.application.dto.openmatch.OpenMatchDtos.MyRequestView;
import com.example.blue_hawk.application.dto.openmatch.OpenMatchDtos.OpenMatchView;
import com.example.blue_hawk.application.dto.openmatch.OpenMatchDtos.RequestView;
import com.example.blue_hawk.application.dto.realtime.RealtimeEvents.MatchChanged;
import com.example.blue_hawk.application.port.RealtimePublisher;
import com.example.blue_hawk.application.usecase.notification.NotificationService;
import com.example.blue_hawk.domain.entity.boardgame.Boardgame;
import com.example.blue_hawk.domain.entity.notification.Notification;
import com.example.blue_hawk.domain.entity.openmatch.OpenMatch;
import com.example.blue_hawk.domain.entity.openmatch.OpenMatchRequest;
import com.example.blue_hawk.domain.entity.user.User;
import com.example.blue_hawk.domain.entity.userboardgame.UserBoardgame;
import com.example.blue_hawk.domain.repository.IBoardgameRepository;
import com.example.blue_hawk.domain.repository.IOpenMatchRepository;
import com.example.blue_hawk.domain.repository.IUserBoardGameRepository;
import com.example.blue_hawk.domain.repository.IUserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class OpenMatchUseCaseImpl implements OpenMatchUseCase {

    static final int CITY_MAX = 100;
    static final int TEXT_MAX = 500;
    /** Até quando no futuro dá para marcar uma partida */
    static final Duration MAX_AHEAD = Duration.ofDays(180);
    private static final int LIST_LIMIT = 100;

    private final IOpenMatchRepository openMatchRepository;
    private final IUserBoardGameRepository userBoardGameRepository;
    private final IBoardgameRepository boardgameRepository;
    private final IUserRepository userRepository;
    private final NotificationService notificationService;
    private final RealtimePublisher realtimePublisher;

    public OpenMatchUseCaseImpl(IOpenMatchRepository openMatchRepository,
                                IUserBoardGameRepository userBoardGameRepository,
                                IBoardgameRepository boardgameRepository,
                                IUserRepository userRepository,
                                NotificationService notificationService,
                                RealtimePublisher realtimePublisher) {
        this.realtimePublisher = realtimePublisher;
        this.openMatchRepository = openMatchRepository;
        this.userBoardGameRepository = userBoardGameRepository;
        this.boardgameRepository = boardgameRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    @Override
    public List<OpenMatchView> list(ListQuery query) {
        CurrentUser currentUser = orAnonymous(query.currentUser());
        Instant now = Instant.now();
        List<OpenMatch> matches;
        if (query.mine()) {
            requireLogin(currentUser);
            matches = openMatchRepository.findUpcomingInvolving(currentUser.userId(), now);
        } else {
            matches = openMatchRepository.findUpcoming(now, query.city(), parseOptionalId(query.boardgameId()), LIST_LIMIT);
        }
        return matches.stream().map(m -> view(m, currentUser)).toList();
    }

    @Override
    public OpenMatchView get(String id, CurrentUser currentUser) {
        return view(findMatch(id), orAnonymous(currentUser));
    }

    @Override
    @Transactional
    public OpenMatchView host(HostCommand command) {
        CurrentUser currentUser = orAnonymous(command.currentUser());
        requireLogin(currentUser);

        UserBoardgame copy = userBoardGameRepository.findById(parseId(command.userBoardgameId(), "userBoardgameId"))
                .orElseThrow(() -> badRequest("userBoardgameId does not exist"));
        if (!currentUser.userId().equals(copy.getUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only host with a game from your shelf");
        }
        Boardgame game = boardgameRepository.findById(copy.getBoardgameId())
                .orElseThrow(() -> badRequest("Boardgame not found"));
        // Partida pública mostra o jogo: só do catálogo (mesma regra da Loja)
        if (!game.isApproved()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only approved games can be hosted");
        }

        String city = requireText(command.city(), "city", CITY_MAX);
        if (city.length() < 2) throw badRequest("city is required");
        Instant scheduledAt = parseFutureInstant(command.scheduledAt());
        int gameLimit = game.getPlayerNumber() != null ? game.getPlayerNumber() : 0;
        if (gameLimit < 2) {
            throw badRequest("This game is single player");
        }
        int maxPlayers = command.maxPlayers() != null ? command.maxPlayers() : gameLimit;
        if (maxPlayers < 2 || maxPlayers > gameLimit) {
            throw badRequest("maxPlayers must be between 2 and " + gameLimit);
        }

        OpenMatch saved = openMatchRepository.save(OpenMatch.host(currentUser.userId(), copy.getId(), city, scheduledAt,
                maxPlayers, optionalText(command.notes(), "notes", TEXT_MAX)));
        return view(saved, currentUser);
    }

    @Override
    @Transactional
    public OpenMatchView cancel(String id, String reason, CurrentUser currentUser) {
        CurrentUser user = orAnonymous(currentUser);
        OpenMatch match = findMatch(id);
        requireHost(match, user);
        String text = reason == null ? "" : reason.trim();
        if (text.length() < CANCEL_REASON_MIN || text.length() > CANCEL_REASON_MAX) {
            throw badRequest("reason must have between " + CANCEL_REASON_MIN + " and " + CANCEL_REASON_MAX
                    + " characters");
        }
        boolean wasOpen = match.getStatus() == OpenMatch.Status.OPEN;
        if (wasOpen) {
            match.cancel(text);
        }
        OpenMatch saved = openMatchRepository.save(match);
        if (wasOpen) {
            // Avisa quem ainda esperava jogar (pendentes e aceitos)
            openMatchRepository.findRequests(match.getId()).stream()
                    .filter(OpenMatchRequest::isActive)
                    .forEach(r -> notificationService.notifyMatch(r.getUserId(), Notification.Type.MATCH_CANCELLED,
                            user.userId(), match.getId()));
            matchChanged(match);
        }
        return view(saved, user);
    }

    @Override
    @Transactional
    public OpenMatchView requestToJoin(String id, String message, CurrentUser currentUser) {
        CurrentUser user = orAnonymous(currentUser);
        requireLogin(user);
        OpenMatch match = findMatch(id);
        if (match.isHostedBy(user.userId())) {
            throw conflict("The host is already in the match");
        }
        if (!match.isAcceptingRequests(Instant.now())) {
            throw conflict("This match is not accepting requests");
        }
        if (seatsLeft(match) <= 0) {
            throw conflict("This match is full");
        }
        String text = requireText(message, "message", TEXT_MAX);

        OpenMatchRequest request = openMatchRepository.findRequest(match.getId(), user.userId())
                .map(existing -> {
                    if (existing.isActive()) throw conflict("You already asked to join this match");
                    if (existing.getStatus() == OpenMatchRequest.Status.REJECTED) {
                        throw conflict("The host declined your request");
                    }
                    existing.resend(text);
                    return existing;
                })
                .orElseGet(() -> OpenMatchRequest.create(match.getId(), user.userId(), text));
        openMatchRepository.saveRequest(request);
        notificationService.notifyMatch(match.getHostId(), Notification.Type.JOIN_REQUEST_RECEIVED, user.userId(),
                match.getId());
        matchChanged(match);
        return view(match, user);
    }

    @Override
    @Transactional
    public OpenMatchView cancelMyRequest(String id, CurrentUser currentUser) {
        CurrentUser user = orAnonymous(currentUser);
        requireLogin(user);
        OpenMatch match = findMatch(id);
        OpenMatchRequest request = openMatchRepository.findRequest(match.getId(), user.userId())
                .filter(OpenMatchRequest::isActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found"));
        request.cancel();
        openMatchRepository.saveRequest(request);
        notificationService.notifyMatch(match.getHostId(), Notification.Type.JOIN_REQUEST_WITHDRAWN, user.userId(),
                match.getId());
        matchChanged(match);
        return view(match, user);
    }

    @Override
    public List<RequestView> listRequests(String id, CurrentUser currentUser) {
        CurrentUser user = orAnonymous(currentUser);
        OpenMatch match = findMatch(id);
        requireHost(match, user);
        return openMatchRepository.findRequests(match.getId()).stream().map(this::requestView).toList();
    }

    @Override
    @Transactional
    public RequestView decide(String requestId, boolean accept, String reply, CurrentUser currentUser) {
        CurrentUser user = orAnonymous(currentUser);
        OpenMatchRequest request = openMatchRepository.findRequestById(parseId(requestId, "requestId"))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found"));
        OpenMatch match = openMatchRepository.findById(request.getOpenMatchId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Match not found"));
        requireHost(match, user);
        if (request.getStatus() != OpenMatchRequest.Status.PENDING) {
            throw conflict("This request was already answered");
        }
        String hostReply = optionalText(reply, "reply", TEXT_MAX);
        if (accept) {
            if (!match.isAcceptingRequests(Instant.now())) throw conflict("This match is not accepting requests");
            if (seatsLeft(match) <= 0) throw conflict("This match is full");
            request.accept(hostReply);
        } else {
            request.reject(hostReply);
        }
        RequestView decided = requestView(openMatchRepository.saveRequest(request));
        notificationService.notifyMatch(request.getUserId(),
                accept ? Notification.Type.JOIN_REQUEST_ACCEPTED : Notification.Type.JOIN_REQUEST_REJECTED,
                user.userId(), match.getId());
        matchChanged(match);
        return decided;
    }

    /**
     * Tempo real ({@code match.changed}, depois do commit): o host e todos com pedido na partida (qualquer status)
     * buscam a partida de novo.
     */
    private void matchChanged(OpenMatch match) {
        Set<UUID> recipients = new LinkedHashSet<>();
        recipients.add(match.getHostId());
        openMatchRepository.findRequests(match.getId()).forEach(r -> recipients.add(r.getUserId()));
        realtimePublisher.publishToUsers(recipients, MatchChanged.of(match.getId().toString()));
    }

    @Override
    public List<PlayedView> playedByMe(CurrentUser currentUser) {
        CurrentUser user = orAnonymous(currentUser);
        requireLogin(user);
        return openMatchRepository.countPlayedByBoardgame(user.userId(), Instant.now()).entrySet().stream()
                .map(e -> new PlayedView(e.getKey().toString(), e.getValue()))
                .toList();
    }

    // ── montagem das views ──────────────────────────────────────────────────

    private OpenMatchView view(OpenMatch match, CurrentUser currentUser) {
        UserBoardgame copy = userBoardGameRepository.findById(match.getUserBoardgameId()).orElse(null);
        Boardgame game = copy != null ? boardgameRepository.findById(copy.getBoardgameId()).orElse(null) : null;
        boolean isHost = match.isHostedBy(currentUser.userId());
        int players = 1 + (int) openMatchRepository.countAccepted(match.getId());

        MyRequestView myRequest = currentUser.authenticated() && !isHost
                ? openMatchRepository.findRequest(match.getId(), currentUser.userId())
                        .map(r -> new MyRequestView(r.getId().toString(), r.getStatus().name(), r.getMessage(),
                                r.getHostReply()))
                        .orElse(null)
                : null;

        GameView gameView = game == null ? null : new GameView(game.getId().toString(), game.getName(),
                game.getCategoryName(), game.getCoverPreset(),
                game.getCoverImageUpdatedAt() != null ? game.getCoverImageUpdatedAt().toEpochMilli() : null,
                game.getPlayerNumber(), game.getPlayTime());

        return new OpenMatchView(
                match.getId().toString(),
                match.getStatus().name(),
                match.getCity(),
                match.getScheduledAt().toString(),
                match.getMaxPlayers(),
                players,
                Math.max(0, match.getMaxPlayers() - players),
                isHost ? (int) openMatchRepository.countPending(match.getId()) : null,
                match.getNotes(),
                new HostView(match.getHostId().toString(), userName(match.getHostId())),
                gameView,
                isHost,
                myRequest,
                match.getCancelReason());
    }

    private RequestView requestView(OpenMatchRequest r) {
        return new RequestView(r.getId().toString(), r.getUserId().toString(), userName(r.getUserId()),
                r.getMessage(), r.getStatus().name(), r.getHostReply(), r.getCreatedAt().toString());
    }

    private String userName(UUID userId) {
        return userRepository.findById(userId).map(User::getName).orElse("Usuário removido");
    }

    private int seatsLeft(OpenMatch match) {
        return match.getMaxPlayers() - 1 - (int) openMatchRepository.countAccepted(match.getId());
    }

    // ── validações ──────────────────────────────────────────────────────────

    private OpenMatch findMatch(String id) {
        return openMatchRepository.findById(parseId(id, "id"))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Match not found"));
    }

    private static void requireHost(OpenMatch match, CurrentUser currentUser) {
        requireLogin(currentUser);
        if (!match.isHostedBy(currentUser.userId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the host can do this");
        }
    }

    private static void requireLogin(CurrentUser currentUser) {
        if (!currentUser.authenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
    }

    private static CurrentUser orAnonymous(CurrentUser currentUser) {
        return currentUser != null ? currentUser : CurrentUser.anonymous();
    }

    static Instant parseFutureInstant(String value) {
        if (value == null || value.isBlank()) throw badRequest("scheduledAt is required");
        Instant instant;
        try {
            instant = OffsetDateTime.parse(value.trim()).toInstant();
        } catch (DateTimeParseException e) {
            throw badRequest("scheduledAt must be an ISO-8601 date-time with offset (ex.: 2026-12-05T22:00:00Z)");
        }
        Instant now = Instant.now();
        if (!instant.isAfter(now)) throw badRequest("scheduledAt must be in the future");
        if (instant.isAfter(now.plus(MAX_AHEAD))) throw badRequest("scheduledAt must be within 180 days");
        return instant;
    }

    private static String requireText(String value, String field, int max) {
        String text = optionalText(value, field, max);
        if (text == null) throw badRequest(field + " is required");
        return text;
    }

    private static String optionalText(String value, String field, int max) {
        if (value == null || value.isBlank()) return null;
        String text = value.trim();
        if (text.length() > max) throw badRequest(field + " must have at most " + max + " characters");
        return text;
    }

    private static UUID parseId(String value, String field) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw badRequest(field + " is invalid");
        }
    }

    private static UUID parseOptionalId(String value) {
        return value == null || value.isBlank() ? null : parseId(value, "boardgameId");
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}

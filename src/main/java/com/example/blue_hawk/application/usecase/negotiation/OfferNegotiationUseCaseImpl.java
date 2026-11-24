package com.example.blue_hawk.application.usecase.negotiation;

import com.example.blue_hawk.application.dto.auth.CurrentUser;
import com.example.blue_hawk.application.dto.negotiation.OfferNegotiationDtos.GameView;
import com.example.blue_hawk.application.dto.negotiation.OfferNegotiationDtos.MessageView;
import com.example.blue_hawk.application.dto.negotiation.OfferNegotiationDtos.NegotiationView;
import com.example.blue_hawk.application.dto.negotiation.OfferNegotiationDtos.OfferView;
import com.example.blue_hawk.application.dto.negotiation.OfferNegotiationDtos.PartyView;
import com.example.blue_hawk.application.usecase.notification.NotificationService;
import com.example.blue_hawk.domain.entity.boardgame.Boardgame;
import com.example.blue_hawk.domain.entity.negotiation.OfferNegotiation;
import com.example.blue_hawk.domain.entity.negotiation.OfferNegotiationMessage;
import com.example.blue_hawk.domain.entity.notification.Notification;
import com.example.blue_hawk.domain.entity.offer.Offer;
import com.example.blue_hawk.domain.entity.user.User;
import com.example.blue_hawk.domain.entity.userboardgame.UserBoardgame;
import com.example.blue_hawk.domain.repository.IBoardgameRepository;
import com.example.blue_hawk.domain.repository.IOfferNegotiationMessageRepository;
import com.example.blue_hawk.domain.repository.IOfferNegotiationRepository;
import com.example.blue_hawk.domain.repository.IOfferRepository;
import com.example.blue_hawk.domain.repository.IUserBoardGameRepository;
import com.example.blue_hawk.domain.repository.IUserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class OfferNegotiationUseCaseImpl implements OfferNegotiationUseCase {

    private final IOfferNegotiationRepository negotiationRepository;
    private final IOfferNegotiationMessageRepository messageRepository;
    private final IOfferRepository offerRepository;
    private final IUserBoardGameRepository userBoardGameRepository;
    private final IBoardgameRepository boardgameRepository;
    private final IUserRepository userRepository;
    private final NotificationService notificationService;

    public OfferNegotiationUseCaseImpl(IOfferNegotiationRepository negotiationRepository,
                                       IOfferNegotiationMessageRepository messageRepository,
                                       IOfferRepository offerRepository,
                                       IUserBoardGameRepository userBoardGameRepository,
                                       IBoardgameRepository boardgameRepository,
                                       IUserRepository userRepository,
                                       NotificationService notificationService) {
        this.negotiationRepository = negotiationRepository;
        this.messageRepository = messageRepository;
        this.offerRepository = offerRepository;
        this.userBoardGameRepository = userBoardGameRepository;
        this.boardgameRepository = boardgameRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    /** Negociação com o anúncio, a cópia anunciada (o dono é o vendedor) e quem está vendo */
    private record Context(OfferNegotiation negotiation, Offer offer, UserBoardgame copy, UUID viewer) {
        UUID sellerId() {
            return copy.getUserId();
        }

        boolean isSeller() {
            return viewer.equals(sellerId());
        }

        boolean isParty() {
            return isSeller() || negotiation.isBuyer(viewer);
        }

        /** A outra parte da negociação */
        UUID otherSide() {
            return isSeller() ? negotiation.getBuyerId() : sellerId();
        }
    }

    @Override
    @Transactional
    public NegotiationView start(String offerId, String body, BigDecimal proposedPrice, CurrentUser currentUser) {
        UUID buyer = requireLogin(currentUser);
        Offer offer = offerRepository.findById(parseId(offerId, "offerId"))
                .orElseThrow(() -> notFound("Offer not found"));
        UserBoardgame copy = copyOf(offer);
        if (buyer.equals(copy.getUserId())) {
            throw forbidden("You cannot negotiate your own offer");
        }
        if (!offer.isActive()) {
            throw conflict("This offer is not available");
        }
        Optional<OfferNegotiation> existing = negotiationRepository.findByOfferAndBuyer(offer.getId(), buyer);
        if (existing.isPresent() && !existing.get().isOpen()) {
            throw conflict("This negotiation is already finished");
        }
        String text = validBody(body);
        BigDecimal price = validPrice(proposedPrice);

        OfferNegotiation negotiation = existing
                .orElseGet(() -> negotiationRepository.save(OfferNegotiation.start(offer.getId(), buyer)));
        Context ctx = new Context(negotiation, offer, copy, buyer);
        post(ctx, text, price);
        return view(ctx, new Names());
    }

    @Override
    public NegotiationView get(String negotiationId, CurrentUser currentUser) {
        Context ctx = load(negotiationId, requireLogin(currentUser));
        requireParty(ctx);
        return view(ctx, new Names());
    }

    @Override
    @Transactional
    public List<MessageView> messages(String negotiationId, String after, CurrentUser currentUser) {
        Context ctx = load(negotiationId, requireLogin(currentUser));
        requireParty(ctx);
        Instant afterInstant = parseOptionalInstant(after);

        List<OfferNegotiationMessage> messages = messageRepository.find(ctx.negotiation().getId(), afterInstant,
                MESSAGES_LIMIT);
        notificationService.markNegotiationRead(ctx.viewer(), ctx.negotiation().getId());

        Names names = new Names();
        return messages.stream().map(m -> messageView(m, ctx.viewer(), names)).toList();
    }

    @Override
    @Transactional
    public MessageView send(String negotiationId, String body, BigDecimal proposedPrice, CurrentUser currentUser) {
        Context ctx = load(negotiationId, requireLogin(currentUser));
        requireParty(ctx);
        OfferNegotiation.Status status = ctx.negotiation().getStatus();
        if (status == OfferNegotiation.Status.CLOSED) {
            throw conflict("This negotiation is closed");
        }
        String text = validBody(body);
        BigDecimal price = validPrice(proposedPrice);
        if (status == OfferNegotiation.Status.ACCEPTED && price != null) {
            throw conflict("The deal is already closed: no new price proposals");
        }
        return messageView(post(ctx, text, price), ctx.viewer(), new Names());
    }

    @Override
    @Transactional
    public NegotiationView accept(String negotiationId, String messageId, CurrentUser currentUser) {
        Context ctx = load(negotiationId, requireLogin(currentUser));
        requireParty(ctx);
        if (!ctx.isSeller()) {
            throw forbidden("Only the seller can accept a proposal");
        }
        UUID id = parseId(messageId, "messageId");
        OfferNegotiation negotiation = ctx.negotiation();
        OfferNegotiationMessage message = messageRepository.findById(id)
                .filter(m -> m.getNegotiationId().equals(negotiation.getId()))
                .orElseThrow(() -> notFound("Message not found"));
        if (!negotiation.isOpen()) {
            throw conflict("This negotiation is not open");
        }
        if (!ctx.offer().isActive()) {
            throw conflict("This offer is not available");
        }
        if (!message.hasProposal() || !negotiation.isBuyer(message.getSenderId())) {
            throw conflict("Only a price proposal sent by the buyer can be accepted");
        }

        UUID seller = ctx.viewer();
        negotiation.accept(message.getProposedPrice(), seller);
        negotiationRepository.save(negotiation);

        Offer offer = ctx.offer();
        offer.markSold();
        offerRepository.save(offer);

        // O anúncio foi vendido: as outras negociações abertas dele terminam
        negotiationRepository.findByOfferAndStatus(offer.getId(), OfferNegotiation.Status.OPEN).stream()
                .filter(other -> !other.getId().equals(negotiation.getId()))
                .forEach(other -> {
                    other.close(seller);
                    negotiationRepository.save(other);
                });

        notificationService.notifyNegotiation(negotiation.getBuyerId(), Notification.Type.OFFER_ACCEPTED, seller,
                negotiation.getId());
        return view(ctx, new Names());
    }

    // ── regras ──────────────────────────────────────────────────────────────

    /** Grava a mensagem e notifica a outra parte (agregando as não lidas) */
    private OfferNegotiationMessage post(Context ctx, String text, BigDecimal price) {
        OfferNegotiationMessage saved = messageRepository.save(
                OfferNegotiationMessage.send(ctx.negotiation().getId(), ctx.viewer(), text, price));
        notificationService.notifyOfferMessage(ctx.otherSide(), ctx.viewer(), ctx.negotiation().getId());
        return saved;
    }

    private Context load(String negotiationId, UUID viewer) {
        OfferNegotiation negotiation = negotiationRepository.findById(parseId(negotiationId, "id"))
                .orElseThrow(() -> notFound("Negotiation not found"));
        Offer offer = offerRepository.findById(negotiation.getOfferId())
                .orElseThrow(() -> notFound("Offer not found"));
        return new Context(negotiation, offer, copyOf(offer), viewer);
    }

    private UserBoardgame copyOf(Offer offer) {
        return userBoardGameRepository.findById(offer.getUserBoardgameId())
                .orElseThrow(() -> notFound("Offer not found"));
    }

    private static void requireParty(Context ctx) {
        if (!ctx.isParty()) {
            throw forbidden("You are not part of this negotiation");
        }
    }

    private static String validBody(String body) {
        String text = body == null ? "" : body.trim();
        if (text.isEmpty() || text.length() > BODY_MAX) {
            throw badRequest("body must have between 1 and " + BODY_MAX + " characters");
        }
        return text;
    }

    /** Opcional; maior que zero e até 99999999.99 (centavos arredondados) */
    private static BigDecimal validPrice(BigDecimal price) {
        if (price == null) return null;
        BigDecimal rounded = price.setScale(2, RoundingMode.HALF_UP);
        if (rounded.signum() <= 0 || rounded.compareTo(PRICE_MAX) > 0) {
            throw badRequest("proposedPrice must be greater than 0 and at most " + PRICE_MAX);
        }
        return rounded;
    }

    // ── montagem das views ──────────────────────────────────────────────────

    private NegotiationView view(Context ctx, Names names) {
        OfferNegotiation n = ctx.negotiation();
        Offer offer = ctx.offer();
        Boardgame game = boardgameRepository.findById(ctx.copy().getBoardgameId()).orElse(null);
        GameView gameView = game == null ? null : new GameView(game.getId().toString(), game.getName(),
                game.getCoverPreset(), coverImageUrl(game));
        return new NegotiationView(
                n.getId().toString(),
                n.getStatus().name(),
                n.getAgreedPrice(),
                ctx.isSeller(),
                new OfferView(offer.getId().toString(), offer.getPrice(), offer.getStatus().name(), gameView),
                new PartyView(n.getBuyerId().toString(), names.of(n.getBuyerId())),
                new PartyView(ctx.sellerId().toString(), names.of(ctx.sellerId())));
    }

    /** Mesmo caminho das demais respostas de jogo: versionado pela data de envio da foto */
    static String coverImageUrl(Boardgame game) {
        return game.getCoverImageUpdatedAt() != null
                ? "/boardgames/" + game.getId() + "/cover?v=" + game.getCoverImageUpdatedAt().toEpochMilli()
                : null;
    }

    private static MessageView messageView(OfferNegotiationMessage m, UUID viewer, Names names) {
        return new MessageView(m.getId().toString(), m.getSenderId().toString(), names.of(m.getSenderId()),
                m.getBody(), m.getProposedPrice(), m.getCreatedAt().toString(), m.getSenderId().equals(viewer));
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

    private static UUID requireLogin(CurrentUser currentUser) {
        if (currentUser == null || !currentUser.authenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return currentUser.userId();
    }

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

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    private static ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}

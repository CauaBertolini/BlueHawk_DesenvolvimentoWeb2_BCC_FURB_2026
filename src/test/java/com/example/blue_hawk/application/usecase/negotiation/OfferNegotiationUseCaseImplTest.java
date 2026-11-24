package com.example.blue_hawk.application.usecase.negotiation;

import com.example.blue_hawk.application.port.RealtimePublisher;
import com.example.blue_hawk.application.dto.auth.CurrentUser;
import com.example.blue_hawk.application.dto.negotiation.OfferNegotiationDtos.MessageView;
import com.example.blue_hawk.application.dto.realtime.RealtimeEvents.ChatMessage;
import com.example.blue_hawk.application.dto.realtime.RealtimeEvents.NegotiationChannel;
import com.example.blue_hawk.application.dto.negotiation.OfferNegotiationDtos.NegotiationView;
import com.example.blue_hawk.application.usecase.notification.NotificationService;
import com.example.blue_hawk.domain.entity.boardgame.Boardgame;
import com.example.blue_hawk.domain.entity.negotiation.OfferNegotiation;
import com.example.blue_hawk.domain.entity.negotiation.OfferNegotiationMessage;
import com.example.blue_hawk.domain.entity.notification.Notification;
import com.example.blue_hawk.domain.entity.offer.Offer;
import com.example.blue_hawk.domain.entity.offer.OfferStatus;
import com.example.blue_hawk.domain.entity.userboardgame.UserBoardgame;
import com.example.blue_hawk.domain.repository.IBoardgameRepository;
import com.example.blue_hawk.domain.repository.IOfferNegotiationMessageRepository;
import com.example.blue_hawk.domain.repository.IOfferNegotiationRepository;
import com.example.blue_hawk.domain.repository.IOfferRepository;
import com.example.blue_hawk.domain.repository.IUserBoardGameRepository;
import com.example.blue_hawk.domain.repository.IUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OfferNegotiationUseCaseImplTest {

    @Mock
    private RealtimePublisher realtimePublisher;

    private static final UUID SELLER = UUID.randomUUID();
    private static final UUID BUYER = UUID.randomUUID();
    private static final UUID OTHER_BUYER = UUID.randomUUID();
    private static final UUID STRANGER = UUID.randomUUID();

    @Mock
    private IOfferNegotiationRepository negotiationRepository;
    @Mock
    private IOfferNegotiationMessageRepository messageRepository;
    @Mock
    private IOfferRepository offerRepository;
    @Mock
    private IUserBoardGameRepository userBoardGameRepository;
    @Mock
    private IBoardgameRepository boardgameRepository;
    @Mock
    private IUserRepository userRepository;
    @Mock
    private NotificationService notificationService;

    private OfferNegotiationUseCaseImpl useCase;
    private Offer offer;
    private OfferNegotiation negotiation;

    private static CurrentUser as(UUID id) {
        return new CurrentUser(id, false);
    }

    @BeforeEach
    void setUp() {
        useCase = new OfferNegotiationUseCaseImpl(negotiationRepository, messageRepository, offerRepository,
                userBoardGameRepository, boardgameRepository, userRepository, notificationService, realtimePublisher);
        Boardgame game = new Boardgame("Catan", "Clássico", 1995, 4, 90);
        UserBoardgame copy = new UserBoardgame(UUID.randomUUID(), SELLER, game.getId());
        offer = new Offer(copy.getId().toString(), new BigDecimal("150.00"), OfferStatus.ACTIVE, null);
        negotiation = OfferNegotiation.start(offer.getId(), BUYER);

        when(offerRepository.findById(offer.getId())).thenReturn(Optional.of(offer));
        when(offerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userBoardGameRepository.findById(copy.getId())).thenReturn(Optional.of(copy));
        when(boardgameRepository.findById(game.getId())).thenReturn(Optional.of(game));
        when(userRepository.findById(any())).thenReturn(Optional.empty());
        when(negotiationRepository.findById(negotiation.getId())).thenReturn(Optional.of(negotiation));
        when(negotiationRepository.findByOfferAndBuyer(any(), any())).thenReturn(Optional.empty());
        when(negotiationRepository.findByOfferAndBuyer(offer.getId(), BUYER)).thenReturn(Optional.of(negotiation));
        when(negotiationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(messageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private String offerId() {
        return offer.getId().toString();
    }

    private String id() {
        return negotiation.getId().toString();
    }

    private static HttpStatus statusOf(Runnable action) {
        return HttpStatus.valueOf(assertThrows(ResponseStatusException.class, action::run).getStatusCode().value());
    }

    /** Mensagem já gravada na negociação, encontrável pelo id */
    private OfferNegotiationMessage stored(UUID sender, String price) {
        OfferNegotiationMessage m = OfferNegotiationMessage.send(negotiation.getId(), sender, "Proposta",
                price != null ? new BigDecimal(price) : null);
        when(messageRepository.findById(m.getId())).thenReturn(Optional.of(m));
        return m;
    }

    // ── começar ─────────────────────────────────────────────────────────────

    @Test
    void newBuyerStartsANegotiationAndNotifiesTheSeller() {
        NegotiationView view = useCase.start(offerId(), "  Faz por 120?  ", new BigDecimal("120"), as(OTHER_BUYER));

        assertEquals("OPEN", view.status());
        assertFalse(view.isSeller());
        assertEquals(OTHER_BUYER.toString(), view.buyer().id());
        assertEquals(SELLER.toString(), view.seller().id());
        assertEquals("Catan", view.offer().game().name());
        assertEquals("ACTIVE", view.offer().status());

        ArgumentCaptor<OfferNegotiationMessage> sent = ArgumentCaptor.forClass(OfferNegotiationMessage.class);
        verify(messageRepository).save(sent.capture());
        assertEquals("Faz por 120?", sent.getValue().getBody());
        assertEquals(new BigDecimal("120.00"), sent.getValue().getProposedPrice());
        verify(notificationService).notifyOfferMessage(eq(SELLER), eq(OTHER_BUYER), any());
    }

    @Test
    void startingAgainReusesTheBuyersNegotiation() {
        NegotiationView view = useCase.start(offerId(), "Ainda disponível?", null, as(BUYER));

        assertEquals(id(), view.id());
        verify(negotiationRepository, never()).save(any());
    }

    @Test
    void cannotNegotiateOwnOffer() {
        assertEquals(HttpStatus.FORBIDDEN, statusOf(() -> useCase.start(offerId(), "Oi", null, as(SELLER))));
    }

    @Test
    void inactiveOfferCannotBeNegotiated() {
        offer.setStatus(OfferStatus.INACTIVE);

        assertEquals(HttpStatus.CONFLICT, statusOf(() -> useCase.start(offerId(), "Oi", null, as(OTHER_BUYER))));
    }

    @Test
    void finishedNegotiationCannotBeRestarted() {
        negotiation.close(SELLER);

        assertEquals(HttpStatus.CONFLICT, statusOf(() -> useCase.start(offerId(), "Oi", null, as(BUYER))));
    }

    @Test
    void validatesBodyAndPrice() {
        assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> useCase.send(id(), "   ", null, as(BUYER))));
        assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> useCase.send(id(), "x".repeat(1001), null, as(BUYER))));
        assertEquals(HttpStatus.BAD_REQUEST,
                statusOf(() -> useCase.send(id(), "Oi", BigDecimal.ZERO, as(BUYER))));
        assertEquals(HttpStatus.BAD_REQUEST,
                statusOf(() -> useCase.send(id(), "Oi", new BigDecimal("100000000.00"), as(BUYER))));
        useCase.send(id(), "Oi", new BigDecimal("99999999.99"), as(BUYER));
    }

    // ── mensagens ───────────────────────────────────────────────────────────

    @Test
    void onlyBuyerAndSellerCanReadOrWrite() {
        assertEquals(HttpStatus.FORBIDDEN, statusOf(() -> useCase.get(id(), as(STRANGER))));
        assertEquals(HttpStatus.FORBIDDEN, statusOf(() -> useCase.messages(id(), null, as(STRANGER))));
        assertEquals(HttpStatus.FORBIDDEN, statusOf(() -> useCase.send(id(), "Oi", null, as(STRANGER))));

        assertTrue(useCase.get(id(), as(SELLER)).isSeller());
    }

    @Test
    void sellerMessageNotifiesTheBuyer() {
        MessageView sent = useCase.send(id(), "Fechado em 140", null, as(SELLER));

        assertTrue(sent.mine());
        assertNull(sent.proposedPrice());
        verify(notificationService).notifyOfferMessage(BUYER, SELLER, negotiation.getId());
    }

    @Test
    void closedNegotiationIsReadOnly() {
        negotiation.close(SELLER);

        useCase.messages(id(), null, as(BUYER));
        assertEquals(HttpStatus.CONFLICT, statusOf(() -> useCase.send(id(), "Ainda dá?", null, as(BUYER))));
    }

    @Test
    void acceptedNegotiationAllowsMessagesButNoNewProposals() {
        negotiation.accept(new BigDecimal("130.00"), SELLER);

        MessageView sent = useCase.send(id(), "Entrego no sábado", null, as(SELLER));
        assertEquals("Entrego no sábado", sent.body());
        assertEquals(HttpStatus.CONFLICT,
                statusOf(() -> useCase.send(id(), "Faz por 100?", new BigDecimal("100"), as(BUYER))));
    }

    @Test
    void readingMarksTheReadersMessageNotificationsAsRead() {
        when(messageRepository.find(eq(negotiation.getId()), isNull(), anyInt())).thenReturn(List.of(
                OfferNegotiationMessage.send(negotiation.getId(), BUYER, "Oi", null)));

        List<MessageView> messages = useCase.messages(id(), null, as(SELLER));

        assertEquals(1, messages.size());
        assertFalse(messages.get(0).mine());
        verify(notificationService).markNegotiationRead(SELLER, negotiation.getId());
    }

    // ── aceitar ─────────────────────────────────────────────────────────────

    @Test
    void acceptingSellsTheOfferClosesOthersAndNotifiesTheBuyer() {
        OfferNegotiation other = OfferNegotiation.start(offer.getId(), OTHER_BUYER);
        when(negotiationRepository.findByOfferAndStatus(offer.getId(), OfferNegotiation.Status.OPEN))
                .thenReturn(List.of(negotiation, other));
        OfferNegotiationMessage proposal = stored(BUYER, "130.00");

        NegotiationView view = useCase.accept(id(), proposal.getId().toString(), as(SELLER));

        assertEquals("ACCEPTED", view.status());
        assertEquals(new BigDecimal("130.00"), view.agreedPrice());
        assertEquals(OfferStatus.SOLD, offer.getStatus());
        assertEquals("SOLD", view.offer().status());
        verify(offerRepository).save(offer);
        assertEquals(OfferNegotiation.Status.CLOSED, other.getStatus());
        assertEquals(SELLER, other.getUpdatedBy());
        assertEquals(SELLER, negotiation.getUpdatedBy());
        verify(negotiationRepository, atLeastOnce()).save(other);
        verify(notificationService).notifyNegotiation(BUYER, Notification.Type.OFFER_ACCEPTED, SELLER,
                negotiation.getId());
    }

    @Test
    void onlyTheSellerCanAccept() {
        OfferNegotiationMessage proposal = stored(BUYER, "130.00");

        assertEquals(HttpStatus.FORBIDDEN,
                statusOf(() -> useCase.accept(id(), proposal.getId().toString(), as(BUYER))));
        assertEquals(HttpStatus.FORBIDDEN,
                statusOf(() -> useCase.accept(id(), proposal.getId().toString(), as(STRANGER))));
        assertEquals(OfferStatus.ACTIVE, offer.getStatus());
    }

    @Test
    void onlyABuyerProposalCanBeAccepted() {
        OfferNegotiationMessage fromSeller = stored(SELLER, "140.00");
        OfferNegotiationMessage withoutPrice = stored(BUYER, null);

        assertEquals(HttpStatus.CONFLICT,
                statusOf(() -> useCase.accept(id(), fromSeller.getId().toString(), as(SELLER))));
        assertEquals(HttpStatus.CONFLICT,
                statusOf(() -> useCase.accept(id(), withoutPrice.getId().toString(), as(SELLER))));
        verify(notificationService, never()).notifyNegotiation(any(), any(), any(), any());
    }

    @Test
    void cannotAcceptWhenNegotiationOrOfferIsNoLongerAvailable() {
        OfferNegotiationMessage proposal = stored(BUYER, "130.00");
        offer.setStatus(OfferStatus.INACTIVE);
        assertEquals(HttpStatus.CONFLICT,
                statusOf(() -> useCase.accept(id(), proposal.getId().toString(), as(SELLER))));

        offer.setStatus(OfferStatus.ACTIVE);
        negotiation.close(SELLER);
        assertEquals(HttpStatus.CONFLICT,
                statusOf(() -> useCase.accept(id(), proposal.getId().toString(), as(SELLER))));
    }

    // ── tempo real ──────────────────────────────────────────────────────────

    @Test
    void messageIsPushedToBuyerAndSellerWithMinePerRecipient() {
        useCase.send(id(), "Faço por 140", null, as(SELLER));

        org.mockito.ArgumentCaptor<Object> toSeller = org.mockito.ArgumentCaptor.forClass(Object.class);
        org.mockito.ArgumentCaptor<Object> toBuyer = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(realtimePublisher).publishToUser(eq(SELLER), toSeller.capture());
        verify(realtimePublisher).publishToUser(eq(BUYER), toBuyer.capture());
        ChatMessage seller = (ChatMessage) toSeller.getValue();
        ChatMessage buyer = (ChatMessage) toBuyer.getValue();
        assertEquals("chat.message", buyer.type());
        assertEquals(NegotiationChannel.of(id()), buyer.channel());
        assertEquals(NegotiationChannel.of(id()), seller.channel());
        assertTrue(((MessageView) seller.message()).mine());
        assertFalse(((MessageView) buyer.message()).mine());
        verify(realtimePublisher, never()).publishToUser(eq(STRANGER), any());
    }
}

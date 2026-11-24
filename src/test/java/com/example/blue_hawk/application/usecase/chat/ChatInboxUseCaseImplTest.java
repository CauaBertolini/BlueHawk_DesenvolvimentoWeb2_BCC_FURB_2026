package com.example.blue_hawk.application.usecase.chat;

import com.example.blue_hawk.application.dto.auth.CurrentUser;
import com.example.blue_hawk.application.dto.chat.ChatInboxDtos.ChatItemView;
import com.example.blue_hawk.domain.entity.boardgame.Boardgame;
import com.example.blue_hawk.domain.entity.direct.DirectConversation;
import com.example.blue_hawk.domain.entity.direct.DirectMessage;
import com.example.blue_hawk.domain.entity.negotiation.OfferNegotiation;
import com.example.blue_hawk.domain.entity.negotiation.OfferNegotiationMessage;
import com.example.blue_hawk.domain.entity.notification.Notification;
import com.example.blue_hawk.domain.entity.offer.Offer;
import com.example.blue_hawk.domain.entity.offer.OfferStatus;
import com.example.blue_hawk.domain.entity.openmatch.OpenMatch;
import com.example.blue_hawk.domain.entity.openmatch.OpenMatchMessage;
import com.example.blue_hawk.domain.entity.openmatch.OpenMatchRequest;
import com.example.blue_hawk.domain.entity.user.User;
import com.example.blue_hawk.domain.entity.user.UserProfile;
import com.example.blue_hawk.domain.entity.user.UserRole;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChatInboxUseCaseImplTest {

    private static final UUID ME = UUID.randomUUID();
    private static final UUID ANA = UUID.randomUUID();
    private static final UUID BOB = UUID.randomUUID();
    private static final UUID HOST = UUID.randomUUID();
    private static final Instant BASE = Instant.parse("2026-11-20T08:00:00Z");

    @Mock
    private IOpenMatchRepository openMatchRepository;
    @Mock
    private IOpenMatchMessageRepository matchMessageRepository;
    @Mock
    private IOfferNegotiationRepository negotiationRepository;
    @Mock
    private IOfferNegotiationMessageRepository negotiationMessageRepository;
    @Mock
    private IOfferRepository offerRepository;
    @Mock
    private IUserBoardGameRepository userBoardGameRepository;
    @Mock
    private IBoardgameRepository boardgameRepository;
    @Mock
    private IUserRepository userRepository;
    @Mock
    private INotificationRepository notificationRepository;
    @Mock
    private IDirectConversationRepository directConversationRepository;
    @Mock
    private IDirectMessageRepository directMessageRepository;
    @Mock
    private IUserProfileRepository profileRepository;

    private ChatInboxUseCaseImpl useCase;

    private static CurrentUser as(UUID id) {
        return new CurrentUser(id, false);
    }

    private static Instant at(int hour) {
        return BASE.plus(hour - 8, ChronoUnit.HOURS);
    }

    @BeforeEach
    void setUp() {
        useCase = new ChatInboxUseCaseImpl(openMatchRepository, matchMessageRepository, negotiationRepository,
                negotiationMessageRepository, offerRepository, userBoardGameRepository, boardgameRepository,
                userRepository, notificationRepository, directConversationRepository, directMessageRepository,
                profileRepository);
        Map<UUID, String> names = Map.of(ME, "Eu", ANA, "Ana Souza", BOB, "Bob", HOST, "Hugo");
        when(userRepository.findById(any())).thenAnswer(inv -> {
            UUID id = inv.getArgument(0);
            return Optional.ofNullable(names.get(id)).map(n -> new User(id, n, n + "@x.com", "h", UserRole.USER));
        });
        when(userBoardGameRepository.findById(any())).thenReturn(Optional.empty());
        when(openMatchRepository.findInvolvingSince(any(), any())).thenReturn(List.of());
        when(negotiationRepository.findInvolving(any())).thenReturn(List.of());
        when(notificationRepository.findUnreadChatMessages(any())).thenReturn(List.of());
    }

    /** Cópia de um jogo com o nome dado, na estante de {@code owner} */
    private UserBoardgame copyOf(UUID owner, String gameName) {
        Boardgame game = new Boardgame(gameName, "", 2000, 4, 60);
        UserBoardgame copy = new UserBoardgame(UUID.randomUUID(), owner, game.getId());
        when(userBoardGameRepository.findById(copy.getId())).thenReturn(Optional.of(copy));
        when(boardgameRepository.findById(game.getId())).thenReturn(Optional.of(game));
        return copy;
    }

    private static OpenMatchMessage matchMessage(OpenMatch match, UUID sender, UUID recipient, String body,
                                                 Instant at) {
        return new OpenMatchMessage(UUID.randomUUID(), match.getId(), sender, recipient, body, at, sender, null, null);
    }

    @Test
    void mixesMatchChatsAndNegotiationsByLatestActivity() {
        // Partida que eu hospedo: mesa + conversa com a Ana (o Bob pediu, mas não há mensagens com ele)
        UserBoardgame azul = copyOf(ME, "Azul");
        OpenMatch hosted = new OpenMatch(UUID.randomUUID(), ME, azul.getId(), "Blumenau",
                Instant.now().plus(2, ChronoUnit.DAYS), 4, null, OpenMatch.Status.OPEN, at(7));
        OpenMatchRequest fromAna = new OpenMatchRequest(UUID.randomUUID(), hosted.getId(), ANA, "posso?",
                OpenMatchRequest.Status.PENDING, null, at(7), null);
        OpenMatchRequest fromBob = new OpenMatchRequest(UUID.randomUUID(), hosted.getId(), BOB, "eu também",
                OpenMatchRequest.Status.PENDING, null, at(7), null);
        when(openMatchRepository.findRequests(hosted.getId())).thenReturn(List.of(fromAna, fromBob));
        when(matchMessageRepository.findLastTable(hosted.getId()))
                .thenReturn(Optional.of(matchMessage(hosted, ME, null, "Bem-vindos", at(9))));
        when(matchMessageRepository.findLastDirect(hosted.getId(), ME, ANA))
                .thenReturn(Optional.of(matchMessage(hosted, ANA, ME, "Levo salgadinho", at(10))));
        when(matchMessageRepository.findLastDirect(hosted.getId(), ME, BOB)).thenReturn(Optional.empty());

        // Partida de outro host em que meu pedido está pendente: só a conversa com o host, ainda vazia
        UserBoardgame dixit = copyOf(HOST, "Dixit");
        OpenMatch joined = new OpenMatch(UUID.randomUUID(), HOST, dixit.getId(), "Gaspar",
                Instant.now().plus(3, ChronoUnit.DAYS), 4, null, OpenMatch.Status.OPEN, at(6));
        OpenMatchRequest mine = new OpenMatchRequest(UUID.randomUUID(), joined.getId(), ME, "oi",
                OpenMatchRequest.Status.PENDING, null, at(8), null);
        when(openMatchRepository.findRequest(joined.getId(), ME)).thenReturn(Optional.of(mine));
        when(matchMessageRepository.findLastDirect(joined.getId(), ME, HOST)).thenReturn(Optional.empty());

        when(openMatchRepository.findInvolvingSince(eq(ME), any())).thenReturn(List.of(hosted, joined));

        // Negociação em que eu compro da Ana
        UserBoardgame catan = copyOf(ANA, "Catan");
        Offer offer = new Offer(catan.getId().toString(), new BigDecimal("150.00"), OfferStatus.ACTIVE, null);
        when(offerRepository.findById(offer.getId())).thenReturn(Optional.of(offer));
        OfferNegotiation negotiation = new OfferNegotiation(UUID.randomUUID(), offer.getId(), ME,
                OfferNegotiation.Status.OPEN, null, at(8), ME, null, null);
        when(negotiationRepository.findInvolving(ME)).thenReturn(List.of(negotiation));
        when(negotiationMessageRepository.findLast(negotiation.getId())).thenReturn(Optional.of(
                new OfferNegotiationMessage(UUID.randomUUID(), negotiation.getId(), ANA, "Faço por 140",
                        new BigDecimal("140.00"), at(11), ANA, null, null)));

        // Não lidas: 2 mensagens na negociação, 3 na conversa com a Ana
        Notification offerUnread = Notification.createForNegotiation(ME, Notification.Type.OFFER_MESSAGE, ANA,
                negotiation.getId());
        offerUnread.aggregate(ANA);
        Notification directUnread = Notification.create(ME, Notification.Type.MATCH_MESSAGE, ANA, hosted.getId(),
                null, ANA);
        directUnread.aggregate(ANA);
        directUnread.aggregate(ANA);
        when(notificationRepository.findUnreadChatMessages(ME)).thenReturn(List.of(offerUnread, directUnread));

        List<ChatItemView> inbox = useCase.inbox(as(ME));

        assertEquals(List.of(
                "neg:" + negotiation.getId(),
                "match:" + hosted.getId() + ":" + ANA,
                "match:" + hosted.getId() + ":table",
                "match:" + joined.getId() + ":" + HOST), inbox.stream().map(ChatItemView::key).toList());

        ChatItemView neg = inbox.get(0);
        assertEquals("NEGOTIATION", neg.kind());
        assertEquals("Catan", neg.title());
        assertEquals("Comprando de Ana Souza", neg.subtitle());
        assertEquals(negotiation.getId().toString(), neg.negotiationId());
        assertEquals(ANA.toString(), neg.withUserId(), "comprador: a outra parte é o vendedor");
        assertNull(neg.matchId());
        assertEquals("OPEN", neg.status());
        assertEquals(2, neg.unread());
        assertEquals(at(11).toString(), neg.lastActivityAt());
        assertEquals("Ana Souza", neg.lastMessage().senderName());
        assertFalse(neg.lastMessage().mine());

        ChatItemView direct = inbox.get(1);
        assertEquals("MATCH_DIRECT", direct.kind());
        assertEquals("Azul", direct.title());
        assertEquals("Ana Souza", direct.subtitle());
        assertEquals(ANA.toString(), direct.withUserId());
        assertEquals(3, direct.unread());

        ChatItemView table = inbox.get(2);
        assertEquals("MATCH_TABLE", table.kind());
        assertEquals("Mesa · Blumenau", table.subtitle());
        assertEquals(0, table.unread());
        assertTrue(table.lastMessage().mine());

        ChatItemView withHost = inbox.get(3);
        assertEquals("Dixit", withHost.title());
        assertEquals("Hugo", withHost.subtitle());
        assertNull(withHost.lastMessage());
        assertEquals(at(8).toString(), withHost.lastActivityAt(), "sem mensagens: a data do pedido");
    }

    @Test
    void sellerSeesTheBuyerAndRejectedRequestWithoutMessagesIsLeftOut() {
        UserBoardgame catan = copyOf(ME, "Catan");
        Offer offer = new Offer(catan.getId().toString(), new BigDecimal("150.00"), OfferStatus.SOLD, null);
        when(offerRepository.findById(offer.getId())).thenReturn(Optional.of(offer));
        OfferNegotiation negotiation = new OfferNegotiation(UUID.randomUUID(), offer.getId(), BOB,
                OfferNegotiation.Status.ACCEPTED, new BigDecimal("140.00"), at(8), BOB, at(9), ME);
        when(negotiationRepository.findInvolving(ME)).thenReturn(List.of(negotiation));
        when(negotiationMessageRepository.findLast(negotiation.getId())).thenReturn(Optional.empty());

        UserBoardgame dixit = copyOf(HOST, "Dixit");
        OpenMatch joined = new OpenMatch(UUID.randomUUID(), HOST, dixit.getId(), "Gaspar",
                Instant.now().plus(3, ChronoUnit.DAYS), 4, null, OpenMatch.Status.OPEN, at(6));
        when(openMatchRepository.findInvolvingSince(eq(ME), any())).thenReturn(List.of(joined));
        when(openMatchRepository.findRequest(joined.getId(), ME)).thenReturn(Optional.of(new OpenMatchRequest(
                UUID.randomUUID(), joined.getId(), ME, "oi", OpenMatchRequest.Status.REJECTED, null, at(7), at(8))));
        when(matchMessageRepository.findLastDirect(joined.getId(), ME, HOST)).thenReturn(Optional.empty());

        List<ChatItemView> inbox = useCase.inbox(as(ME));

        assertEquals(1, inbox.size());
        assertEquals("Vendendo para Bob", inbox.get(0).subtitle());
        assertEquals(BOB.toString(), inbox.get(0).withUserId(), "vendedor: a outra parte é o comprador");
        assertEquals("ACCEPTED", inbox.get(0).status());
        assertEquals(at(8).toString(), inbox.get(0).lastActivityAt());
    }

    @Test
    void unreadCountCountsMessageNotifications() {
        when(notificationRepository.countUnreadChatMessages(ME)).thenReturn(4L);

        assertEquals(4L, useCase.unreadCount(as(ME)));
    }

    @Test
    void requiresLogin() {
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> useCase.inbox(CurrentUser.anonymous()));
        assertEquals(HttpStatus.UNAUTHORIZED.value(), e.getStatusCode().value());
    }

    @Test
    void includesDirectConversationsAndPlayerToPlayerMatchThreads() {
        // Conversa direta com a Ana, com mensagem e 2 não lidas; a Ana desligou as mensagens diretas
        DirectConversation withAna = new DirectConversation(UUID.randomUUID(), ME, ANA, at(12), at(7), ANA, at(12),
                ANA);
        when(directMessageRepository.findLast(withAna.getId())).thenReturn(Optional.of(
                new DirectMessage(UUID.randomUUID(), withAna.getId(), ANA, "Bora jogar?", at(12), ANA, null, null)));
        UserProfile anaProfile = UserProfile.defaults(ANA);
        anaProfile.setAllowDirectMessages(false);
        when(profileRepository.findByUserId(ANA)).thenReturn(Optional.of(anaProfile));
        // Conversa vazia que o Bob abriu comigo: fica de fora até a primeira mensagem
        DirectConversation fromBob = new DirectConversation(UUID.randomUUID(), ME, BOB, null, at(13), BOB, null,
                null);
        // Conversa vazia que eu abri com o Hugo: aparece para mim
        DirectConversation toHost = new DirectConversation(UUID.randomUUID(), ME, HOST, null, at(4), ME, null, null);
        when(directConversationRepository.findInvolving(ME)).thenReturn(List.of(withAna, fromBob, toHost));
        Notification dmUnread = Notification.createForDirect(ME, ANA, withAna.getId());
        dmUnread.aggregate(ANA);

        // Partida do Hugo em que eu e o Bob estamos aceitos e já conversamos
        UserBoardgame dixit = copyOf(HOST, "Dixit");
        OpenMatch joined = new OpenMatch(UUID.randomUUID(), HOST, dixit.getId(), "Gaspar",
                Instant.now().plus(3, ChronoUnit.DAYS), 4, null, OpenMatch.Status.OPEN, at(5));
        OpenMatchRequest mine = new OpenMatchRequest(UUID.randomUUID(), joined.getId(), ME, "oi",
                OpenMatchRequest.Status.ACCEPTED, null, at(5), at(5));
        OpenMatchRequest bob = new OpenMatchRequest(UUID.randomUUID(), joined.getId(), BOB, "oi",
                OpenMatchRequest.Status.ACCEPTED, null, at(5), at(5));
        when(openMatchRepository.findInvolvingSince(eq(ME), any())).thenReturn(List.of(joined));
        when(openMatchRepository.findRequest(joined.getId(), ME)).thenReturn(Optional.of(mine));
        when(openMatchRepository.findRequests(joined.getId())).thenReturn(List.of(mine, bob));
        when(matchMessageRepository.findLastTable(joined.getId())).thenReturn(Optional.empty());
        when(matchMessageRepository.findLastDirect(joined.getId(), ME, HOST)).thenReturn(Optional.empty());
        when(matchMessageRepository.findLastDirect(joined.getId(), ME, BOB))
                .thenReturn(Optional.of(matchMessage(joined, BOB, ME, "Levo pipoca", at(10))));
        Notification bobUnread = Notification.create(ME, Notification.Type.MATCH_MESSAGE, BOB, joined.getId(), null,
                BOB);
        when(notificationRepository.findUnreadChatMessages(ME)).thenReturn(List.of(dmUnread, bobUnread));

        List<ChatItemView> inbox = useCase.inbox(as(ME));

        assertEquals(List.of(
                "dm:" + withAna.getId(),
                "match:" + joined.getId() + ":" + BOB,
                "match:" + joined.getId() + ":" + HOST,
                "match:" + joined.getId() + ":table",
                "dm:" + toHost.getId()), inbox.stream().map(ChatItemView::key).toList());

        ChatItemView dm = inbox.get(0);
        assertEquals("DIRECT", dm.kind());
        assertEquals("Ana Souza", dm.title());
        assertEquals(withAna.getId().toString(), dm.directConversationId());
        assertEquals(ANA.toString(), dm.withUserId());
        assertNull(dm.matchId());
        assertNull(dm.negotiationId());
        assertEquals("CLOSED", dm.status(), "a Ana não aceita mensagens diretas");
        assertEquals(2, dm.unread());
        assertEquals(at(12).toString(), dm.lastActivityAt());
        assertEquals("Bora jogar?", dm.lastMessage().body());

        ChatItemView withBob = inbox.get(1);
        assertEquals("MATCH_DIRECT", withBob.kind());
        assertEquals(BOB.toString(), withBob.withUserId());
        assertEquals(1, withBob.unread());
        assertNull(withBob.directConversationId());

        ChatItemView empty = inbox.get(4);
        assertEquals("OPEN", empty.status());
        assertNull(empty.lastMessage());
    }
}

package com.example.blue_hawk.application.usecase.notification;

import com.example.blue_hawk.application.dto.auth.CurrentUser;
import com.example.blue_hawk.application.dto.notification.NotificationDtos.NotificationView;
import com.example.blue_hawk.domain.entity.notification.Notification;
import com.example.blue_hawk.domain.entity.openmatch.OpenMatch;
import com.example.blue_hawk.domain.repository.IBoardgameRepository;
import com.example.blue_hawk.domain.repository.INotificationRepository;
import com.example.blue_hawk.domain.repository.IOfferNegotiationRepository;
import com.example.blue_hawk.domain.repository.IOfferRepository;
import com.example.blue_hawk.domain.repository.IOpenMatchRepository;
import com.example.blue_hawk.domain.repository.IUserBoardGameRepository;
import com.example.blue_hawk.domain.repository.IUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificationUseCaseImplTest {

    private static final UUID ME = UUID.randomUUID();
    private static final UUID HOST = UUID.randomUUID();

    @Mock
    private INotificationRepository notificationRepository;
    @Mock
    private IUserRepository userRepository;
    @Mock
    private IOpenMatchRepository openMatchRepository;
    @Mock
    private IUserBoardGameRepository userBoardGameRepository;
    @Mock
    private IBoardgameRepository boardgameRepository;
    @Mock
    private IOfferNegotiationRepository negotiationRepository;
    @Mock
    private IOfferRepository offerRepository;

    private NotificationUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        useCase = new NotificationUseCaseImpl(notificationRepository, userRepository, openMatchRepository,
                userBoardGameRepository, boardgameRepository, negotiationRepository, offerRepository);
        when(userRepository.findById(any())).thenReturn(Optional.empty());
        when(userBoardGameRepository.findById(any())).thenReturn(Optional.empty());
    }

    @Test
    void matchCancelledShowsTheReasonAndDirectMessageShowsTheConversation() {
        OpenMatch cancelled = OpenMatch.host(HOST, UUID.randomUUID(), "Blumenau",
                Instant.now().plus(1, ChronoUnit.DAYS), 4, null);
        cancelled.cancel("Choveu muito, a estrada fechou");
        OpenMatch open = OpenMatch.host(HOST, UUID.randomUUID(), "Gaspar",
                Instant.now().plus(2, ChronoUnit.DAYS), 4, null);
        when(openMatchRepository.findById(cancelled.getId())).thenReturn(Optional.of(cancelled));
        when(openMatchRepository.findById(open.getId())).thenReturn(Optional.of(open));
        UUID conversation = UUID.randomUUID();
        when(notificationRepository.findByUser(eq(ME), anyInt(), anyInt())).thenReturn(List.of(
                Notification.create(ME, Notification.Type.MATCH_CANCELLED, HOST, cancelled.getId(), null, null),
                Notification.create(ME, Notification.Type.JOIN_REQUEST_ACCEPTED, HOST, open.getId(), null, null),
                Notification.createForDirect(ME, HOST, conversation)));

        List<NotificationView> views = useCase.list(0, 20, new CurrentUser(ME, false));

        assertEquals("Choveu muito, a estrada fechou", views.get(0).match().cancelReason());
        assertNull(views.get(0).directConversationId());
        assertNull(views.get(1).match().cancelReason(), "partida aberta: sem motivo");
        assertEquals("DIRECT_MESSAGE", views.get(2).type());
        assertEquals(conversation.toString(), views.get(2).directConversationId());
        assertNull(views.get(2).match());
    }
}

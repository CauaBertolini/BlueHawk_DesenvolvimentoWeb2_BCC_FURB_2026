package com.example.blue_hawk.application.usecase.user;

import com.example.blue_hawk.application.dto.auth.CurrentUser;
import com.example.blue_hawk.application.dto.user.UserProfileDtos.PublicProfileView;
import com.example.blue_hawk.application.dto.user.UserProfileDtos.ShelfItem;
import com.example.blue_hawk.domain.entity.boardgame.Boardgame;
import com.example.blue_hawk.domain.entity.offer.Offer;
import com.example.blue_hawk.domain.entity.offer.OfferStatus;
import com.example.blue_hawk.domain.entity.user.User;
import com.example.blue_hawk.domain.entity.user.UserProfile;
import com.example.blue_hawk.domain.entity.user.UserRole;
import com.example.blue_hawk.domain.entity.userboardgame.UserBoardgame;
import com.example.blue_hawk.domain.repository.IBoardgameRepository;
import com.example.blue_hawk.domain.repository.IOfferRepository;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PublicProfileUseCaseImplTest {

    private static final UUID ANA = UUID.randomUUID();
    private static final UUID ME = UUID.randomUUID();
    private static final Instant JOINED = Instant.parse("2026-03-01T12:00:00Z");

    @Mock
    private IUserRepository userRepository;
    @Mock
    private IUserProfileRepository profileRepository;
    @Mock
    private IUserBoardGameRepository userBoardGameRepository;
    @Mock
    private IBoardgameRepository boardgameRepository;
    @Mock
    private IOfferRepository offerRepository;
    @Mock
    private IOpenMatchRepository openMatchRepository;

    private PublicProfileUseCaseImpl useCase;
    private Offer catanOffer;

    @BeforeEach
    void setUp() {
        useCase = new PublicProfileUseCaseImpl(userRepository, profileRepository, userBoardGameRepository,
                boardgameRepository, offerRepository, openMatchRepository);

        User ana = new User(ANA, "Ana Souza", "ana@x.com", "hash", UserRole.USER);
        ana.setCreatedAt(JOINED);
        when(userRepository.findById(ANA)).thenReturn(Optional.of(ana));
        when(userRepository.findById(ME)).thenReturn(Optional.empty());
        when(profileRepository.findByUserId(any())).thenReturn(Optional.empty());

        // Estante da Ana: Catan (catálogo, anunciado), um jogo dela pendente e um pendente de outra pessoa
        Boardgame catan = new Boardgame("Catan", "Desc", 2020, 4, 60);
        catan.setCategoryName("Estratégia");
        catan.approve();
        catan.setCoverImageUpdatedAt(Instant.ofEpochMilli(1234));
        Boardgame own = Boardgame.submittedBy(ANA, "Caseiro", "X", 2024, 2, 30);
        Boardgame foreign = Boardgame.submittedBy(UUID.randomUUID(), "Alheio", "X", 2024, 2, 30);
        UserBoardgame c1 = copy(catan);
        UserBoardgame c2 = copy(own);
        UserBoardgame c3 = copy(foreign);
        when(userBoardGameRepository.findAll(eq(ANA), any(), anyInt(), anyInt()))
                .thenReturn(new PageImpl<>(List.of(c1, c2, c3)));

        catanOffer = new Offer(UUID.randomUUID(), c1.getId(), new BigDecimal("150.00"), OfferStatus.ACTIVE, null,
                LocalDateTime.now(), LocalDateTime.now());
        when(offerRepository.findAll(eq(ANA), any(), eq(OfferStatus.ACTIVE), anyInt(), anyInt()))
                .thenReturn(new PageImpl<>(List.of(catanOffer)));
        when(openMatchRepository.countPlayedByBoardgame(eq(ANA), any()))
                .thenReturn(Map.of(catan.getId(), 2L, own.getId(), 1L));
    }

    private UserBoardgame copy(Boardgame game) {
        UserBoardgame copy = new UserBoardgame(ANA.toString(), game.getId().toString());
        when(boardgameRepository.findById(game.getId())).thenReturn(Optional.of(game));
        return copy;
    }

    private void anaProfile(boolean isPublic, boolean showShelf) {
        when(profileRepository.findByUserId(ANA)).thenReturn(Optional.of(new UserProfile(ANA, "Oi!", "Blumenau",
                UserProfile.ExperienceLevel.VETERANO, "Estratégia", isPublic, showShelf, true, false,
                false, "dice-red", null, null, JOINED, ANA, null, null)));
    }

    private static CurrentUser as(UUID id) {
        return new CurrentUser(id, false);
    }

    @Test
    void publicProfileShowsProfileStatsAndShelfWithOffers() {
        anaProfile(true, true);

        PublicProfileView view = useCase.get(ANA.toString(), as(ME));

        assertEquals("Ana Souza", view.name());
        assertEquals("USER", view.role());
        assertEquals("dice-red", view.avatarPreset());
        assertNull(view.avatarUrl());
        assertTrue(view.isPublic());
        assertFalse(view.isMe());
        assertEquals("Blumenau", view.profile().city());
        assertEquals("VETERANO", view.profile().experienceLevel());
        assertEquals(JOINED.toString(), view.profile().memberSince());
        assertFalse(view.acceptsDirectMessages(), "a Ana desligou as mensagens diretas");
        assertEquals(2, view.stats().games(), "o pendente de outra pessoa não entra");
        assertEquals(1, view.stats().forSale());
        assertEquals(3, view.stats().matchesPlayed());

        assertEquals(2, view.shelf().size());
        ShelfItem catan = view.shelf().get(0);
        assertEquals("Catan", catan.name());
        assertEquals("Estratégia", catan.category());
        assertTrue(catan.coverImageUrl().endsWith("/cover?v=1234"));
        assertEquals(catanOffer.getId().toString(), catan.offerId());
        assertEquals(new BigDecimal("150.00"), catan.price());
        assertNull(view.shelf().get(1).offerId());
        assertNull(view.shelf().get(1).price());
    }

    @Test
    void defaultsWhenThereIsNoProfileRow() {
        PublicProfileView view = useCase.get(ANA.toString(), as(ME));

        assertTrue(view.isPublic());
        assertNotNull(view.profile());
        assertNull(view.profile().bio());
        assertNotNull(view.shelf());
    }

    @Test
    void privateProfileHidesProfileStatsAndShelf() {
        anaProfile(false, true);

        PublicProfileView view = useCase.get(ANA.toString(), as(ME));

        assertEquals("Ana Souza", view.name());
        assertEquals("dice-red", view.avatarPreset());
        assertFalse(view.isPublic());
        assertNull(view.profile());
        assertNull(view.stats());
        assertNull(view.shelf());
    }

    @Test
    void hiddenShelfKeepsProfileAndStats() {
        anaProfile(true, false);

        PublicProfileView view = useCase.get(ANA.toString(), as(ME));

        assertNotNull(view.profile());
        assertEquals(2, view.stats().games());
        assertNull(view.shelf());
    }

    @Test
    void ownerAlwaysSeesEverything() {
        anaProfile(false, false);

        PublicProfileView view = useCase.get(ANA.toString(), as(ANA));

        assertTrue(view.isMe());
        assertFalse(view.isPublic());
        assertNotNull(view.profile());
        assertNotNull(view.stats());
        assertEquals(2, view.shelf().size());
    }

    @Test
    void requiresLoginAndExistingUser() {
        assertEquals(HttpStatus.UNAUTHORIZED, assertThrows(ResponseStatusException.class,
                () -> useCase.get(ANA.toString(), CurrentUser.anonymous())).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                () -> useCase.get(ME.toString(), as(ANA))).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                () -> useCase.get("nope", as(ANA))).getStatusCode());
    }

    @Test
    void acceptsDirectMessagesByDefaultWhenThereIsNoProfileRow() {
        assertTrue(useCase.get(ANA.toString(), as(ME)).acceptsDirectMessages());
    }
}

package com.example.blue_hawk.application.usecase.auth;

import com.example.blue_hawk.application.dto.auth.CurrentUser;
import com.example.blue_hawk.application.dto.match.CreateMatchCommand;
import com.example.blue_hawk.application.dto.match.DeleteMatchCommand;
import com.example.blue_hawk.application.dto.match.UpdateMatchCommand;
import com.example.blue_hawk.application.dto.matchParticipant.CreateMatchParticipantCommand;
import com.example.blue_hawk.application.dto.matchParticipant.DeleteMatchParticipantCommand;
import com.example.blue_hawk.application.dto.matchParticipant.UpdateMatchParticipantCommand;
import com.example.blue_hawk.application.dto.offer.CreateOfferCommand;
import com.example.blue_hawk.application.dto.offer.DeleteOfferCommand;
import com.example.blue_hawk.application.dto.offer.PatchOfferCommand;
import com.example.blue_hawk.application.dto.offer.UpdateOfferCommand;
import com.example.blue_hawk.application.dto.userboardgame.DeleteUserBoardgameCommand;
import com.example.blue_hawk.application.dto.userboardgame.UpdateUserBoardgameCommand;
import com.example.blue_hawk.application.usecase.match.CreateMatchUseCaseImpl;
import com.example.blue_hawk.application.usecase.match.DeleteMatchUseCaseImpl;
import com.example.blue_hawk.application.usecase.match.UpdateMatchUseCaseImpl;
import com.example.blue_hawk.application.usecase.matchParticipant.CreateMatchParticipantUseCaseImpl;
import com.example.blue_hawk.application.usecase.matchParticipant.DeleteMatchParticipantUseCaseImpl;
import com.example.blue_hawk.application.usecase.matchParticipant.UpdateMatchParticipantUseCaseImpl;
import com.example.blue_hawk.application.usecase.offer.CreateOfferUseCaseImpl;
import com.example.blue_hawk.application.usecase.offer.DeleteOfferUseCaseImpl;
import com.example.blue_hawk.application.usecase.offer.PatchOfferUseCaseImpl;
import com.example.blue_hawk.application.usecase.offer.UpdateOfferUseCaseImpl;
import com.example.blue_hawk.application.usecase.userboardgame.DeleteUserBoardgameUseCaseImpl;
import com.example.blue_hawk.application.usecase.userboardgame.UpdateUserBoardgameUseCaseImpl;
import com.example.blue_hawk.domain.entity.boardgame.Boardgame;
import com.example.blue_hawk.domain.entity.match.Match;
import com.example.blue_hawk.domain.entity.matchparticipant.MatchParticipant;
import com.example.blue_hawk.domain.entity.offer.Offer;
import com.example.blue_hawk.domain.entity.offer.OfferStatus;
import com.example.blue_hawk.domain.entity.userboardgame.UserBoardgame;
import com.example.blue_hawk.domain.repository.IBoardgameRepository;
import com.example.blue_hawk.domain.repository.IMatchParticipantRepository;
import com.example.blue_hawk.domain.repository.IMatchRepository;
import com.example.blue_hawk.domain.repository.IOfferRepository;
import com.example.blue_hawk.domain.repository.IUserBoardGameRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Estante, anúncios, partidas e participações só mudam pelas mãos do dono (ou de um ADMIN):
 * estar logado não basta para mexer no que é de outra pessoa.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OwnershipAccessTest {

    private static final UUID OWNER_ID = UUID.randomUUID();
    private static final UUID PLAYER_ID = UUID.randomUUID();
    private static final CurrentUser OWNER = new CurrentUser(OWNER_ID, false);
    private static final CurrentUser PLAYER = new CurrentUser(PLAYER_ID, false);
    private static final CurrentUser STRANGER = new CurrentUser(UUID.randomUUID(), false);
    private static final CurrentUser ADMIN = new CurrentUser(UUID.randomUUID(), true);

    @Mock
    private IOfferRepository offers;
    @Mock
    private IUserBoardGameRepository copies;
    @Mock
    private IBoardgameRepository games;
    @Mock
    private IMatchRepository matches;
    @Mock
    private IMatchParticipantRepository participants;

    private UserBoardgame copy;
    private UserBoardgame strangersCopy;
    private Offer offer;
    private Match match;
    private MatchParticipant participant;

    @BeforeEach
    void setUp() {
        Boardgame catan = new Boardgame("Catan", "Desc", 1995, 4, 90);
        copy = new UserBoardgame(UUID.randomUUID(), OWNER_ID, catan.getId());
        strangersCopy = new UserBoardgame(UUID.randomUUID(), STRANGER.userId(), catan.getId());
        offer = new Offer(UUID.randomUUID(), copy.getId(), new BigDecimal("150.00"), OfferStatus.ACTIVE,
                "Cópia completa", LocalDateTime.now(), LocalDateTime.now());
        match = new Match(UUID.randomUUID(), copy.getId());
        participant = new MatchParticipant(UUID.randomUUID(), match.getId(), PLAYER_ID);

        when(games.findById(catan.getId())).thenReturn(Optional.of(catan));
        when(copies.findById(copy.getId())).thenReturn(Optional.of(copy));
        when(copies.findById(strangersCopy.getId())).thenReturn(Optional.of(strangersCopy));
        when(copies.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(offers.findById(offer.getId())).thenReturn(Optional.of(offer));
        when(offers.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(matches.findById(match.getId())).thenReturn(Optional.of(match));
        when(matches.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(participants.findById(participant.getId())).thenReturn(Optional.of(participant));
        when(participants.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private static void assertStatus(HttpStatus expected, Executable action) {
        assertEquals(expected, assertThrows(ResponseStatusException.class, action).getStatusCode());
    }

    // ── anúncios ───────────────────────────────────────────────────────────

    private void createOffer(CurrentUser user) {
        new CreateOfferUseCaseImpl(offers, copies, games).handle(new CreateOfferCommand(copy.getId().toString(),
                new BigDecimal("150.00"), null, "Cópia completa, cartas com sleeves", "BOM", "A_COMBINAR", user));
    }

    private void patchOffer(CurrentUser user) {
        new PatchOfferUseCaseImpl(offers, copies).handle(new PatchOfferCommand(offer.getId().toString(),
                new BigDecimal("1.00"), null, null, null, null, user));
    }

    private void updateOffer(String copyId, CurrentUser user) {
        new UpdateOfferUseCaseImpl(offers, copies).handle(new UpdateOfferCommand(offer.getId().toString(), copyId,
                new BigDecimal("1.00"), "ACTIVE", "Curta", null, null, user));
    }

    private void deleteOffer(CurrentUser user) {
        new DeleteOfferUseCaseImpl(offers, copies).handle(new DeleteOfferCommand(offer.getId().toString(), user));
    }

    @Test
    void onlyTheCopyOwnerOrAdminSellsIt() {
        assertStatus(HttpStatus.FORBIDDEN, () -> createOffer(STRANGER));
        assertStatus(HttpStatus.UNAUTHORIZED, () -> createOffer(CurrentUser.anonymous()));
        assertStatus(HttpStatus.UNAUTHORIZED, () -> createOffer(null));
        verify(offers, never()).save(any());

        assertDoesNotThrow(() -> createOffer(OWNER));
        assertDoesNotThrow(() -> createOffer(ADMIN));
    }

    @Test
    void strangerCannotEditOrDeleteSomeoneElsesOffer() {
        assertStatus(HttpStatus.FORBIDDEN, () -> patchOffer(STRANGER));
        assertStatus(HttpStatus.FORBIDDEN, () -> updateOffer(copy.getId().toString(), STRANGER));
        assertStatus(HttpStatus.FORBIDDEN, () -> deleteOffer(STRANGER));
        assertStatus(HttpStatus.UNAUTHORIZED, () -> deleteOffer(CurrentUser.anonymous()));

        verify(offers, never()).save(any());
        verify(offers, never()).deleteById(any());
    }

    @Test
    void ownerAndAdminEditAndDeleteTheOffer() {
        assertDoesNotThrow(() -> patchOffer(OWNER));
        assertDoesNotThrow(() -> updateOffer(copy.getId().toString(), OWNER));
        assertDoesNotThrow(() -> patchOffer(ADMIN));
        assertDoesNotThrow(() -> deleteOffer(OWNER));
        verify(offers).deleteById(offer.getId());
    }

    @Test
    void ownerCannotMoveTheOfferToSomeoneElsesCopy() {
        assertStatus(HttpStatus.FORBIDDEN, () -> updateOffer(strangersCopy.getId().toString(), OWNER));
        verify(offers, never()).save(any());
    }

    // ── estante ────────────────────────────────────────────────────────────

    private void updateCopy(UUID newOwner, CurrentUser user) {
        new UpdateUserBoardgameUseCaseImpl(copies).handle(new UpdateUserBoardgameCommand(copy.getId().toString(),
                newOwner.toString(), copy.getBoardgameId().toString(), user));
    }

    private void deleteCopy(CurrentUser user) {
        new DeleteUserBoardgameUseCaseImpl(copies).handle(new DeleteUserBoardgameCommand(copy.getId().toString(), user));
    }

    @Test
    void strangerCannotChangeOrRemoveSomeoneElsesCopy() {
        assertStatus(HttpStatus.FORBIDDEN, () -> updateCopy(STRANGER.userId(), STRANGER));
        assertStatus(HttpStatus.FORBIDDEN, () -> deleteCopy(STRANGER));
        assertStatus(HttpStatus.UNAUTHORIZED, () -> deleteCopy(CurrentUser.anonymous()));

        verify(copies, never()).save(any());
        verify(copies, never()).deleteById(any());
    }

    @Test
    void ownerCannotHandTheCopyToAnotherUser() {
        assertStatus(HttpStatus.FORBIDDEN, () -> updateCopy(STRANGER.userId(), OWNER));
        verify(copies, never()).save(any());
    }

    @Test
    void ownerAndAdminManageTheCopy() {
        assertDoesNotThrow(() -> updateCopy(OWNER_ID, OWNER));
        assertDoesNotThrow(() -> deleteCopy(OWNER));
        verify(copies).deleteById(copy.getId());
        // ADMIN pode até passar a cópia para outra pessoa
        assertDoesNotThrow(() -> updateCopy(STRANGER.userId(), ADMIN));
    }

    // ── partidas ───────────────────────────────────────────────────────────

    private void createMatch(CurrentUser user) {
        new CreateMatchUseCaseImpl(matches, copies, games)
                .handle(new CreateMatchCommand(copy.getId().toString(), user));
    }

    private void updateMatch(UUID copyId, CurrentUser user) {
        new UpdateMatchUseCaseImpl(matches, copies, games)
                .handle(new UpdateMatchCommand(match.getId().toString(), copyId.toString(), user));
    }

    private void deleteMatch(CurrentUser user) {
        new DeleteMatchUseCaseImpl(matches, copies).handle(new DeleteMatchCommand(match.getId().toString(), user));
    }

    @Test
    void onlyTheHostOrAdminManagesTheMatch() {
        assertStatus(HttpStatus.FORBIDDEN, () -> createMatch(STRANGER));
        assertStatus(HttpStatus.FORBIDDEN, () -> updateMatch(copy.getId(), STRANGER));
        assertStatus(HttpStatus.FORBIDDEN, () -> updateMatch(strangersCopy.getId(), STRANGER));
        assertStatus(HttpStatus.FORBIDDEN, () -> updateMatch(strangersCopy.getId(), OWNER));
        assertStatus(HttpStatus.FORBIDDEN, () -> deleteMatch(STRANGER));
        assertStatus(HttpStatus.UNAUTHORIZED, () -> deleteMatch(CurrentUser.anonymous()));
        verify(matches, never()).save(any());
        verify(matches, never()).deleteById(any());

        assertDoesNotThrow(() -> createMatch(OWNER));
        assertDoesNotThrow(() -> updateMatch(copy.getId(), OWNER));
        assertDoesNotThrow(() -> deleteMatch(ADMIN));
    }

    // ── participações ──────────────────────────────────────────────────────

    private void join(UUID userId, CurrentUser user) {
        new CreateMatchParticipantUseCaseImpl(participants, matches, copies, games)
                .handle(new CreateMatchParticipantCommand(match.getId().toString(), userId.toString(), user));
    }

    private void updateParticipant(UUID userId, CurrentUser user) {
        new UpdateMatchParticipantUseCaseImpl(participants, matches, copies).handle(new UpdateMatchParticipantCommand(
                participant.getId().toString(), match.getId().toString(), userId.toString(), user));
    }

    private void removeParticipant(CurrentUser user) {
        new DeleteMatchParticipantUseCaseImpl(participants, matches, copies)
                .handle(new DeleteMatchParticipantCommand(participant.getId().toString(), user));
    }

    @Test
    void usersJoinMatchesOnlyAsThemselves() {
        assertStatus(HttpStatus.FORBIDDEN, () -> join(PLAYER_ID, STRANGER));
        assertStatus(HttpStatus.UNAUTHORIZED, () -> join(PLAYER_ID, CurrentUser.anonymous()));
        verify(participants, never()).save(any());

        assertDoesNotThrow(() -> join(PLAYER_ID, PLAYER));
        assertDoesNotThrow(() -> join(PLAYER_ID, ADMIN));
    }

    @Test
    void onlyThePlayerTheHostOrAdminRemovesAParticipant() {
        assertStatus(HttpStatus.FORBIDDEN, () -> removeParticipant(STRANGER));
        assertStatus(HttpStatus.UNAUTHORIZED, () -> removeParticipant(null));
        verify(participants, never()).deleteById(any());

        assertDoesNotThrow(() -> removeParticipant(PLAYER));
        assertDoesNotThrow(() -> removeParticipant(OWNER));
        assertDoesNotThrow(() -> removeParticipant(ADMIN));
    }

    @Test
    void participationCannotBeReassignedToAnotherUser() {
        assertStatus(HttpStatus.FORBIDDEN, () -> updateParticipant(STRANGER.userId(), STRANGER));
        assertStatus(HttpStatus.FORBIDDEN, () -> updateParticipant(STRANGER.userId(), PLAYER));
        verify(participants, never()).save(any());

        assertDoesNotThrow(() -> updateParticipant(PLAYER_ID, PLAYER));
        assertDoesNotThrow(() -> updateParticipant(STRANGER.userId(), ADMIN));
    }
}

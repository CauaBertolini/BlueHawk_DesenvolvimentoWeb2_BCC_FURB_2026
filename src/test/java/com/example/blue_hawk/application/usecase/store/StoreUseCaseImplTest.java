package com.example.blue_hawk.application.usecase.store;

import com.example.blue_hawk.application.dto.auth.CurrentUser;
import com.example.blue_hawk.domain.entity.boardgame.Boardgame;
import com.example.blue_hawk.domain.entity.offer.Offer;
import com.example.blue_hawk.domain.entity.offer.OfferCondition;
import com.example.blue_hawk.domain.entity.offer.DeliveryMethod;
import com.example.blue_hawk.domain.entity.offer.OfferStatus;
import com.example.blue_hawk.domain.entity.userboardgame.UserBoardgame;
import com.example.blue_hawk.domain.repository.IBoardgameRepository;
import com.example.blue_hawk.domain.repository.IOfferRepository;
import com.example.blue_hawk.domain.repository.IUserBoardGameRepository;
import com.example.blue_hawk.domain.repository.IUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StoreUseCaseImplTest {

    private static final UUID SELLER = UUID.randomUUID();

    @Mock
    private IOfferRepository offerRepository;
    @Mock
    private IUserBoardGameRepository userBoardGameRepository;
    @Mock
    private IBoardgameRepository boardgameRepository;
    @Mock
    private IUserRepository userRepository;

    private StoreUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        useCase = new StoreUseCaseImpl(offerRepository, userBoardGameRepository, boardgameRepository, userRepository);
        when(userRepository.findById(any())).thenReturn(Optional.empty());
    }

    private Offer offerFor(Boardgame game, int price) {
        UserBoardgame copy = new UserBoardgame(SELLER.toString(), game.getId().toString());
        when(userBoardGameRepository.findById(copy.getId())).thenReturn(Optional.of(copy));
        when(boardgameRepository.findById(game.getId())).thenReturn(Optional.of(game));
        return new Offer(UUID.randomUUID(), copy.getId(), BigDecimal.valueOf(price), OfferStatus.ACTIVE, null,
                LocalDateTime.now(), LocalDateTime.now());
    }

    private Boardgame game(String name, String category) {
        Boardgame game = new Boardgame(name, "Desc", 2020, 4, 60);
        game.setCategoryName(category);
        return game;
    }

    @Test
    void showsOnlyApprovedGamesAndFlagsMyOffers() {
        Boardgame pending = Boardgame.submittedBy(SELLER, "Caseiro", "X", 2024, 2, 30);
        List<Offer> offers = List.of(offerFor(game("Catan", "Estratégia"), 180), offerFor(pending, 50));
        when(offerRepository.findActiveForStore(anyBoolean(), anyInt())).thenReturn(offers);

        var result = useCase.list(StoreUseCase.Sort.RECENT, null, null, 24, new CurrentUser(SELLER, false));

        assertEquals(1, result.size());
        assertEquals("Catan", result.get(0).game().name());
        assertEquals(SELLER.toString(), result.get(0).sellerId());
        assertTrue(result.get(0).isMine());
    }

    @Test
    void filtersByNameAndCategoryAndRespectsLimit() {
        List<Offer> offers = List.of(
                offerFor(game("Catan", "Estratégia"), 180),
                offerFor(game("Dixit", "Party"), 90),
                offerFor(game("Catan: Cidades", "Estratégia"), 120));
        when(offerRepository.findActiveForStore(anyBoolean(), anyInt())).thenReturn(offers);

        assertEquals(2, useCase.list(StoreUseCase.Sort.PRICE, "catan", null, 24, CurrentUser.anonymous()).size());
        assertEquals(1, useCase.list(StoreUseCase.Sort.PRICE, null, "party", 24, CurrentUser.anonymous()).size());
        assertEquals(1, useCase.list(StoreUseCase.Sort.PRICE, null, null, 1, CurrentUser.anonymous()).size());
    }

    @Test
    void listExposesConditionAndDeliveryMethod() {
        Offer detailed = offerFor(game("Catan", "Estratégia"), 180);
        detailed.setCondition(OfferCondition.MUITO_BOM);
        detailed.setDeliveryMethod(DeliveryMethod.ENVIO);
        Offer legacy = offerFor(game("Dixit", "Party"), 90);
        when(offerRepository.findActiveForStore(anyBoolean(), anyInt())).thenReturn(List.of(detailed, legacy));

        var result = useCase.list(StoreUseCase.Sort.RECENT, null, null, 24, CurrentUser.anonymous());

        assertEquals("MUITO_BOM", result.get(0).condition());
        assertEquals("ENVIO", result.get(0).deliveryMethod());
        // Anúncio antigo: sem estado e sem forma de entrega
        assertNull(result.get(1).condition());
        assertNull(result.get(1).deliveryMethod());
    }

    // ── página do anúncio ──────────────────────────────────────────────────

    @Test
    void getExposesConditionAndDeliveryMethod() {
        Offer offer = offerFor(game("Azul", "Abstrato"), 150);
        offer.setCondition(OfferCondition.NOVO_LACRADO);
        offer.setDeliveryMethod(DeliveryMethod.RETIRADA);
        when(offerRepository.findById(offer.getId())).thenReturn(Optional.of(offer));

        var result = useCase.get(offer.getId().toString(), null).orElseThrow();

        assertEquals("NOVO_LACRADO", result.condition());
        assertEquals("RETIRADA", result.deliveryMethod());
    }

    @Test
    void getsOneActiveOfferWithSeller() {
        Offer offer = offerFor(game("Azul", "Abstrato"), 150);
        when(offerRepository.findById(offer.getId())).thenReturn(Optional.of(offer));

        var result = useCase.get(offer.getId().toString(), null);

        assertTrue(result.isPresent());
        assertEquals("Azul", result.get().game().name());
        assertEquals(SELLER.toString(), result.get().sellerId());
        assertEquals(false, result.get().isMine());
    }

    @Test
    void soldPendingOrUnknownOffersAreUnavailable() {
        Offer sold = offerFor(game("Azul", "Abstrato"), 150);
        sold.markSold();
        when(offerRepository.findById(sold.getId())).thenReturn(Optional.of(sold));
        Offer pendingGame = offerFor(Boardgame.submittedBy(SELLER, "Caseiro", "X", 2024, 2, 30), 50);
        when(offerRepository.findById(pendingGame.getId())).thenReturn(Optional.of(pendingGame));

        assertTrue(useCase.get(sold.getId().toString(), null).isEmpty());
        assertTrue(useCase.get(pendingGame.getId().toString(), null).isEmpty());
        assertTrue(useCase.get(UUID.randomUUID().toString(), null).isEmpty());
        assertTrue(useCase.get("nao-e-uuid", null).isEmpty());
    }
}

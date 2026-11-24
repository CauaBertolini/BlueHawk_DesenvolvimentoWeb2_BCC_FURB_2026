package com.example.blue_hawk.application.usecase.offer;

import com.example.blue_hawk.application.dto.auth.CurrentUser;

import com.example.blue_hawk.application.dto.offer.CreateOfferCommand;
import com.example.blue_hawk.application.dto.offer.CreateOfferOutput;
import com.example.blue_hawk.domain.entity.boardgame.Boardgame;
import com.example.blue_hawk.domain.entity.offer.DeliveryMethod;
import com.example.blue_hawk.domain.entity.offer.Offer;
import com.example.blue_hawk.domain.entity.offer.OfferCondition;
import com.example.blue_hawk.domain.entity.userboardgame.UserBoardgame;
import com.example.blue_hawk.domain.repository.IBoardgameRepository;
import com.example.blue_hawk.domain.repository.IOfferRepository;
import com.example.blue_hawk.domain.repository.IUserBoardGameRepository;
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
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CreateOfferUseCaseImplTest {

    private static final UUID SELLER = UUID.randomUUID();
    private static final String DESCRIPTION = "Cópia completa, cartas com sleeves";

    @Mock
    private IOfferRepository offerRepository;
    @Mock
    private IUserBoardGameRepository userBoardGameRepository;
    @Mock
    private IBoardgameRepository boardgameRepository;

    private CreateOfferUseCaseImpl useCase;
    private UserBoardgame copy;

    @BeforeEach
    void setUp() {
        useCase = new CreateOfferUseCaseImpl(offerRepository, userBoardGameRepository, boardgameRepository);
        Boardgame catan = new Boardgame("Catan", "Desc", 1995, 4, 90);
        copy = new UserBoardgame(SELLER.toString(), catan.getId().toString());
        when(userBoardGameRepository.findById(copy.getId())).thenReturn(Optional.of(copy));
        when(boardgameRepository.findById(catan.getId())).thenReturn(Optional.of(catan));
        when(offerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private CreateOfferCommand command(String description, String condition, String deliveryMethod) {
        return new CreateOfferCommand(copy.getId().toString(), new BigDecimal("150.00"), null, description,
                condition, deliveryMethod, new CurrentUser(SELLER, false));
    }

    private void assertBadRequest(CreateOfferCommand command) {
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> useCase.handle(command));
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
        verify(offerRepository, never()).save(any());
    }

    @Test
    void createsWithConditionAndDeliveryMethod() {
        CreateOfferOutput output = useCase.handle(command("  " + DESCRIPTION + "  ", "MUITO_BOM", "ENVIO"));

        assertEquals("MUITO_BOM", output.condition());
        assertEquals("ENVIO", output.deliveryMethod());
        assertEquals(DESCRIPTION, output.description());

        // Gravados como enum no anúncio
        ArgumentCaptor<Offer> saved = ArgumentCaptor.forClass(Offer.class);
        verify(offerRepository).save(saved.capture());
        assertEquals(OfferCondition.MUITO_BOM, saved.getValue().getCondition());
        assertEquals(DeliveryMethod.ENVIO, saved.getValue().getDeliveryMethod());
    }

    @Test
    void acceptsAnyCase() {
        CreateOfferOutput output = useCase.handle(command(DESCRIPTION, "como_novo", " Retirada "));

        assertEquals("COMO_NOVO", output.condition());
        assertEquals("RETIRADA", output.deliveryMethod());
    }

    @Test
    void conditionIsRequiredAndMustBeKnown() {
        assertBadRequest(command(DESCRIPTION, null, "ENVIO"));
        assertBadRequest(command(DESCRIPTION, "  ", "ENVIO"));
        assertBadRequest(command(DESCRIPTION, "SEMINOVO", "ENVIO"));
    }

    @Test
    void deliveryMethodIsRequiredAndMustBeKnown() {
        assertBadRequest(command(DESCRIPTION, "BOM", null));
        assertBadRequest(command(DESCRIPTION, "BOM", ""));
        assertBadRequest(command(DESCRIPTION, "BOM", "CORREIOS"));
    }

    @Test
    void descriptionIsRequiredWithTenToFiveHundredChars() {
        assertBadRequest(command(null, "BOM", "ENVIO"));
        assertBadRequest(command("          ", "BOM", "ENVIO"));
        assertBadRequest(command("  curta   ", "BOM", "ENVIO"));
        assertBadRequest(command("x".repeat(501), "BOM", "ENVIO"));

        assertEquals(10, useCase.handle(command("x".repeat(10), "BOM", "ENVIO")).description().length());
        assertEquals(500, useCase.handle(command("x".repeat(500), "BOM", "ENVIO")).description().length());
    }
}

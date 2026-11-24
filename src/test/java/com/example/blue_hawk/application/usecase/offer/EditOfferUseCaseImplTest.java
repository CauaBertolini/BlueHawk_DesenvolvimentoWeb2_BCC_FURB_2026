package com.example.blue_hawk.application.usecase.offer;

import com.example.blue_hawk.application.dto.auth.CurrentUser;
import com.example.blue_hawk.domain.repository.IUserBoardGameRepository;

import com.example.blue_hawk.application.dto.offer.GetOfferQuery;
import com.example.blue_hawk.application.dto.offer.ListOfferQuery;
import com.example.blue_hawk.application.dto.offer.PatchOfferCommand;
import com.example.blue_hawk.application.dto.offer.PatchOfferOutput;
import com.example.blue_hawk.application.dto.offer.UpdateOfferCommand;
import com.example.blue_hawk.application.dto.offer.UpdateOfferOutput;
import com.example.blue_hawk.domain.entity.offer.DeliveryMethod;
import com.example.blue_hawk.domain.entity.offer.Offer;
import com.example.blue_hawk.domain.entity.offer.OfferCondition;
import com.example.blue_hawk.domain.entity.offer.OfferStatus;
import com.example.blue_hawk.domain.repository.IOfferRepository;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Edição (PUT/PATCH) e leitura (GET/lista) dos detalhes do anúncio: estado de conservação e forma de entrega. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EditOfferUseCaseImplTest {

    /** ADMIN edita qualquer anúncio: as regras de dono ficam no OwnershipAccessTest */
    private static final CurrentUser ADMIN = new CurrentUser(UUID.randomUUID(), true);

    @Mock
    private IOfferRepository offerRepository;
    @Mock
    private IUserBoardGameRepository userBoardGameRepository;

    private Offer offer;
    private Offer legacy;

    @BeforeEach
    void setUp() {
        offer = new Offer(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("150.00"), OfferStatus.ACTIVE,
                "Cópia completa", LocalDateTime.now(), LocalDateTime.now());
        offer.setCondition(OfferCondition.BOM);
        offer.setDeliveryMethod(DeliveryMethod.A_COMBINAR);
        // Anúncio antigo: sem estado e sem forma de entrega
        legacy = new Offer(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("80.00"), OfferStatus.ACTIVE,
                null, LocalDateTime.now(), LocalDateTime.now());
        when(offerRepository.findById(offer.getId())).thenReturn(Optional.of(offer));
        when(offerRepository.findById(legacy.getId())).thenReturn(Optional.of(legacy));
        when(offerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private PatchOfferCommand patch(Offer target, String condition, String deliveryMethod) {
        return new PatchOfferCommand(target.getId().toString(), new BigDecimal("120.00"), null, null,
                condition, deliveryMethod, ADMIN);
    }

    private UpdateOfferCommand update(Offer target, String condition, String deliveryMethod) {
        return new UpdateOfferCommand(target.getId().toString(), target.getUserBoardgameId().toString(),
                new BigDecimal("120.00"), "ACTIVE", "Curta", condition, deliveryMethod, ADMIN);
    }

    // ── PATCH ──────────────────────────────────────────────────────────────

    @Test
    void patchKeepsConditionAndDeliveryWhenAbsent() {
        PatchOfferOutput output = new PatchOfferUseCaseImpl(offerRepository, userBoardGameRepository).handle(patch(offer, null, null));

        assertEquals(new BigDecimal("120.00"), output.price());
        assertEquals("BOM", output.condition());
        assertEquals("A_COMBINAR", output.deliveryMethod());
    }

    @Test
    void patchReplacesConditionAndDeliveryWhenPresent() {
        PatchOfferOutput output = new PatchOfferUseCaseImpl(offerRepository, userBoardGameRepository)
                .handle(patch(offer, "novo_lacrado", "retirada"));

        assertEquals("NOVO_LACRADO", output.condition());
        assertEquals("RETIRADA", output.deliveryMethod());
        assertEquals(DeliveryMethod.RETIRADA, offer.getDeliveryMethod());
    }

    @Test
    void patchValidatesLikeCreate() {
        PatchOfferUseCaseImpl useCase = new PatchOfferUseCaseImpl(offerRepository, userBoardGameRepository);

        ResponseStatusException badCondition = assertThrows(ResponseStatusException.class,
                () -> useCase.handle(patch(offer, "QUEBRADO", null)));
        assertEquals(HttpStatus.BAD_REQUEST, badCondition.getStatusCode());
        ResponseStatusException badDelivery = assertThrows(ResponseStatusException.class,
                () -> useCase.handle(patch(offer, null, "")));
        assertEquals(HttpStatus.BAD_REQUEST, badDelivery.getStatusCode());
        verify(offerRepository, never()).save(any());
    }

    // ── PUT ────────────────────────────────────────────────────────────────

    @Test
    void updateKeepsValuesWhenAbsentAndDoesNotRequireDescriptionRules() {
        UpdateOfferOutput output = new UpdateOfferUseCaseImpl(offerRepository, userBoardGameRepository).handle(update(legacy, null, null));

        assertNull(output.condition());
        assertNull(output.deliveryMethod());
        assertEquals("Curta", output.description());
    }

    @Test
    void updateReplacesValuesWhenPresent() {
        UpdateOfferOutput output = new UpdateOfferUseCaseImpl(offerRepository, userBoardGameRepository)
                .handle(update(offer, "COM_MARCAS_DE_USO", "Envio"));

        assertEquals("COM_MARCAS_DE_USO", output.condition());
        assertEquals("ENVIO", output.deliveryMethod());
    }

    @Test
    void updateRejectsInvalidDeliveryMethod() {
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> new UpdateOfferUseCaseImpl(offerRepository, userBoardGameRepository).handle(update(offer, null, "DRONE")));
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
        verify(offerRepository, never()).save(any());
    }

    // ── leitura ────────────────────────────────────────────────────────────

    @Test
    void getAndListExposeConditionAndDelivery() {
        var got = new GetOfferUseCaseImpl(offerRepository).handle(new GetOfferQuery(offer.getId().toString()));
        assertEquals("BOM", got.condition());
        assertEquals("A_COMBINAR", got.deliveryMethod());

        when(offerRepository.findAll(any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new PageImpl<>(List.of(offer, legacy)));
        var listed = new ListOfferUseCaseImpl(offerRepository).handle(new ListOfferQuery(null, null, null, 0, 10));
        assertEquals("BOM", listed.get(0).condition());
        assertEquals("A_COMBINAR", listed.get(0).deliveryMethod());
        assertNull(listed.get(1).condition());
        assertNull(listed.get(1).deliveryMethod());
    }
}

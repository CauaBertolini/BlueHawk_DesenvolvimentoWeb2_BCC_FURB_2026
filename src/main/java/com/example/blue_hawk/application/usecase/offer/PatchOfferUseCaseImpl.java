package com.example.blue_hawk.application.usecase.offer;

import com.example.blue_hawk.application.usecase.auth.Access;
import com.example.blue_hawk.domain.repository.IUserBoardGameRepository;

import com.example.blue_hawk.application.dto.offer.PatchOfferCommand;
import com.example.blue_hawk.application.dto.offer.PatchOfferOutput;
import com.example.blue_hawk.domain.entity.offer.Offer;
import com.example.blue_hawk.domain.entity.offer.OfferStatus;
import com.example.blue_hawk.domain.repository.IOfferRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class PatchOfferUseCaseImpl implements PatchOfferUseCase {

    private final IOfferRepository offerRepository;

        private final IUserBoardGameRepository userBoardGameRepository;

    public PatchOfferUseCaseImpl(IOfferRepository offerRepository, IUserBoardGameRepository userBoardGameRepository) {
        this.userBoardGameRepository = userBoardGameRepository;
        this.offerRepository = offerRepository;
    }

    @Override
    public PatchOfferOutput handle(PatchOfferCommand command) {
        Offer offer = offerRepository
                .findById(UUID.fromString(command.id()))
                .orElseThrow(() -> new RuntimeException("Offer not found with id: " + command.id()));
        // Só o dono do anúncio (dono da cópia) ou ADMIN altera
        Access.requireOfferOwner(offer, userBoardGameRepository, command.currentUser());

        if (command.price() != null) {
            offer.setPrice(command.price());
        }
        if (command.status() != null) {
            offer.setStatus(OfferStatus.valueOf(command.status()));
        }
        if (command.description() != null) {
            offer.setDescription(command.description());
        }
        // Quando vierem, estado e forma de entrega seguem as regras da criação (400)
        if (command.condition() != null) {
            offer.setCondition(OfferFields.parseCondition(command.condition()));
        }
        if (command.deliveryMethod() != null) {
            offer.setDeliveryMethod(OfferFields.parseDeliveryMethod(command.deliveryMethod()));
        }

        Offer updated = offerRepository.save(offer);

        return new PatchOfferOutput(
                updated.getId().toString(),
                updated.getUserBoardgameId().toString(),
                updated.getPrice(),
                updated.getStatus().name(),
                updated.getDescription(),
                OfferFields.nameOf(updated.getCondition()),
                OfferFields.nameOf(updated.getDeliveryMethod()),
                updated.getCreatedAt(),
                updated.getUpdatedAt()
        );
    }
}

package com.example.blue_hawk.application.usecase.offer;

import com.example.blue_hawk.application.usecase.auth.Access;
import com.example.blue_hawk.domain.repository.IUserBoardGameRepository;

import com.example.blue_hawk.application.dto.offer.UpdateOfferCommand;
import com.example.blue_hawk.application.dto.offer.UpdateOfferOutput;
import com.example.blue_hawk.domain.entity.offer.Offer;
import com.example.blue_hawk.domain.entity.offer.OfferStatus;
import com.example.blue_hawk.domain.repository.IOfferRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class UpdateOfferUseCaseImpl implements UpdateOfferUseCase {

    private final IOfferRepository offerRepository;

        private final IUserBoardGameRepository userBoardGameRepository;

    public UpdateOfferUseCaseImpl(IOfferRepository offerRepository, IUserBoardGameRepository userBoardGameRepository) {
        this.userBoardGameRepository = userBoardGameRepository;
        this.offerRepository = offerRepository;
    }

    @Override
    public UpdateOfferOutput handle(UpdateOfferCommand command) {
        Offer offer = offerRepository
                .findById(UUID.fromString(command.id()))
                .orElseThrow(() -> new RuntimeException("Offer not found with id: " + command.id()));
        // Só o dono do anúncio (dono da cópia) ou ADMIN altera
        Access.requireOfferOwner(offer, userBoardGameRepository, command.currentUser());

        // Trocar a cópia do anúncio: a nova também precisa ser de quem edita
        UUID newCopyId = UUID.fromString(command.userBoardgameId());
        if (!newCopyId.equals(offer.getUserBoardgameId())) {
            Access.requireShelfOwner(userBoardGameRepository, newCopyId, command.currentUser());
        }
        offer.setUserBoardgameId(newCopyId);
        offer.setPrice(command.price());
        offer.setStatus(OfferStatus.valueOf(command.status()));
        offer.setDescription(command.description());
        // Estado e forma de entrega são opcionais na edição (anúncios antigos não têm): null mantém os atuais
        if (command.condition() != null) {
            offer.setCondition(OfferFields.parseCondition(command.condition()));
        }
        if (command.deliveryMethod() != null) {
            offer.setDeliveryMethod(OfferFields.parseDeliveryMethod(command.deliveryMethod()));
        }

        Offer updated = offerRepository.save(offer);

        return new UpdateOfferOutput(
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

package com.example.blue_hawk.application.usecase.offer;

import com.example.blue_hawk.application.dto.offer.CreateOfferCommand;
import com.example.blue_hawk.application.dto.offer.CreateOfferOutput;
import com.example.blue_hawk.domain.entity.boardgame.Boardgame;
import com.example.blue_hawk.domain.entity.offer.DeliveryMethod;
import com.example.blue_hawk.domain.entity.offer.Offer;
import com.example.blue_hawk.domain.entity.offer.OfferCondition;
import com.example.blue_hawk.domain.entity.offer.OfferStatus;
import com.example.blue_hawk.domain.entity.userboardgame.UserBoardgame;
import com.example.blue_hawk.domain.repository.IBoardgameRepository;
import com.example.blue_hawk.domain.repository.IOfferRepository;
import com.example.blue_hawk.domain.repository.IUserBoardGameRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
public class CreateOfferUseCaseImpl implements CreateOfferUseCase {

    private final IOfferRepository offerRepository;
    private final IUserBoardGameRepository userBoardGameRepository;
    private final IBoardgameRepository boardgameRepository;

    public CreateOfferUseCaseImpl(IOfferRepository offerRepository,
                                  IUserBoardGameRepository userBoardGameRepository,
                                  IBoardgameRepository boardgameRepository) {
        this.offerRepository = offerRepository;
        this.userBoardGameRepository = userBoardGameRepository;
        this.boardgameRepository = boardgameRepository;
    }

    @Override
    public CreateOfferOutput handle(CreateOfferCommand command) {
        if (command.userBoardgameId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "userBoardgameId is required");
        }
        // Detalhes obrigatórios do anúncio: estado de conservação, forma de entrega e descrição (400)
        OfferCondition condition = OfferFields.parseCondition(command.condition());
        DeliveryMethod deliveryMethod = OfferFields.parseDeliveryMethod(command.deliveryMethod());
        String description = OfferFields.requireDescription(command.description());

        UserBoardgame copy = userBoardGameRepository.findById(UUID.fromString(command.userBoardgameId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "UserBoardgame not found"));
        Boardgame boardgame = boardgameRepository.findById(copy.getBoardgameId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Boardgame not found"));
        // A Loja só anuncia jogos do catálogo: um jogo ainda em aprovação não pode ser vendido
        if (!boardgame.isApproved()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only approved games can be offered for sale");
        }

        OfferStatus status = command.status() != null
                ? OfferStatus.valueOf(command.status())
                : OfferStatus.ACTIVE;

        Offer offer = new Offer(
                command.userBoardgameId(),
                command.price(),
                status,
                description
        );
        offer.setCondition(condition);
        offer.setDeliveryMethod(deliveryMethod);

        Offer saved = offerRepository.save(offer);

        return new CreateOfferOutput(
                saved.getId().toString(),
                saved.getUserBoardgameId().toString(),
                saved.getPrice(),
                saved.getStatus().name(),
                saved.getDescription(),
                OfferFields.nameOf(saved.getCondition()),
                OfferFields.nameOf(saved.getDeliveryMethod()),
                saved.getCreatedAt(),
                saved.getUpdatedAt()
        );
    }
}

package com.example.blue_hawk.application.usecase.offer;

import com.example.blue_hawk.application.usecase.auth.Access;
import com.example.blue_hawk.domain.repository.IUserBoardGameRepository;
import com.example.blue_hawk.domain.entity.offer.Offer;

import com.example.blue_hawk.application.dto.offer.DeleteOfferCommand;
import com.example.blue_hawk.domain.repository.IOfferRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class DeleteOfferUseCaseImpl implements DeleteOfferUseCase {

    private final IOfferRepository offerRepository;

        private final IUserBoardGameRepository userBoardGameRepository;

    public DeleteOfferUseCaseImpl(IOfferRepository offerRepository, IUserBoardGameRepository userBoardGameRepository) {
        this.userBoardGameRepository = userBoardGameRepository;
        this.offerRepository = offerRepository;
    }

    @Override
    public void handle(DeleteOfferCommand command) {
        UUID id = UUID.fromString(command.id());
        Offer offer = offerRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Offer not found with id: " + command.id()));
        // Só o dono do anúncio (dono da cópia) ou ADMIN apaga
        Access.requireOfferOwner(offer, userBoardGameRepository, command.currentUser());
        offerRepository.deleteById(id);
    }
}

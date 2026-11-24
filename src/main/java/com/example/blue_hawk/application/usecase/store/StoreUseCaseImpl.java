package com.example.blue_hawk.application.usecase.store;

import com.example.blue_hawk.application.dto.auth.CurrentUser;
import com.example.blue_hawk.application.usecase.offer.OfferFields;
import com.example.blue_hawk.domain.entity.boardgame.Boardgame;
import com.example.blue_hawk.domain.entity.offer.Offer;
import com.example.blue_hawk.domain.entity.user.User;
import com.example.blue_hawk.domain.entity.userboardgame.UserBoardgame;
import com.example.blue_hawk.domain.repository.IBoardgameRepository;
import com.example.blue_hawk.domain.repository.IOfferRepository;
import com.example.blue_hawk.domain.repository.IUserBoardGameRepository;
import com.example.blue_hawk.domain.repository.IUserRepository;
import org.springframework.stereotype.Service;

import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class StoreUseCaseImpl implements StoreUseCase {

    /** Quantos anúncios ativos são lidos para filtrar (a Loja mostra no máximo {@code MAX_LIMIT}) */
    private static final int SCAN_LIMIT = 300;
    static final int MAX_LIMIT = 60;

    private final IOfferRepository offerRepository;
    private final IUserBoardGameRepository userBoardGameRepository;
    private final IBoardgameRepository boardgameRepository;
    private final IUserRepository userRepository;

    public StoreUseCaseImpl(IOfferRepository offerRepository, IUserBoardGameRepository userBoardGameRepository,
                            IBoardgameRepository boardgameRepository, IUserRepository userRepository) {
        this.offerRepository = offerRepository;
        this.userBoardGameRepository = userBoardGameRepository;
        this.boardgameRepository = boardgameRepository;
        this.userRepository = userRepository;
    }

    @Override
    public List<StoreOffer> list(Sort sort, String query, String category, int limit, CurrentUser currentUser) {
        int max = Math.max(1, Math.min(limit, MAX_LIMIT));
        String needle = query == null || query.isBlank() ? null : query.trim().toLowerCase(Locale.ROOT);
        String categoryName = category == null || category.isBlank() ? null : category.trim();
        UUID me = currentUser != null ? currentUser.userId() : null;

        // Caches da requisição: vários anúncios podem ser do mesmo jogo ou do mesmo vendedor
        Map<UUID, Optional<Boardgame>> games = new HashMap<>();
        Map<UUID, String> sellers = new HashMap<>();

        List<StoreOffer> result = new ArrayList<>();
        for (Offer offer : offerRepository.findActiveForStore(sort == Sort.PRICE, SCAN_LIMIT)) {
            if (result.size() >= max) break;
            UserBoardgame copy = userBoardGameRepository.findById(offer.getUserBoardgameId()).orElse(null);
            if (copy == null) continue;
            Boardgame game = games.computeIfAbsent(copy.getBoardgameId(), boardgameRepository::findById).orElse(null);
            // Só jogos do catálogo aparecem na Loja
            if (game == null || !game.isApproved()) continue;
            if (needle != null && !game.getName().toLowerCase(Locale.ROOT).contains(needle)) continue;
            if (categoryName != null && !categoryName.equalsIgnoreCase(game.getCategoryName())) continue;

            String seller = sellers.computeIfAbsent(copy.getUserId(),
                    id -> userRepository.findById(id).map(User::getName).orElse("Usuário removido"));
            result.add(toStoreOffer(offer, copy, game, seller, me));
        }
        return result;
    }

    @Override
    public Optional<StoreOffer> get(String offerId, CurrentUser currentUser) {
        UUID id;
        try {
            id = UUID.fromString(offerId);
        } catch (IllegalArgumentException | NullPointerException e) {
            return Optional.empty();
        }
        UUID me = currentUser != null ? currentUser.userId() : null;
        return offerRepository.findById(id)
                .filter(Offer::isActive)
                .flatMap(offer -> userBoardGameRepository.findById(offer.getUserBoardgameId())
                        .flatMap(copy -> boardgameRepository.findById(copy.getBoardgameId())
                                .filter(Boardgame::isApproved)
                                .map(game -> toStoreOffer(offer, copy, game,
                                        userRepository.findById(copy.getUserId()).map(User::getName)
                                                .orElse("Usuário removido"),
                                        me))));
    }

    private static StoreOffer toStoreOffer(Offer offer, UserBoardgame copy, Boardgame game, String seller, UUID me) {
        return new StoreOffer(
                offer.getId().toString(),
                offer.getPrice(),
                offer.getDescription(),
                OfferFields.nameOf(offer.getCondition()),
                OfferFields.nameOf(offer.getDeliveryMethod()),
                offer.getCreatedAt() != null ? offer.getCreatedAt().toInstant(ZoneOffset.UTC).toString() : null,
                copy.getUserId().toString(),
                seller,
                copy.getUserId().equals(me),
                new StoreGame(game.getId().toString(), game.getName(), game.getDescription(),
                        game.getCategoryName(), game.getCoverPreset(),
                        game.getCoverImageUpdatedAt() != null ? game.getCoverImageUpdatedAt().toEpochMilli() : null,
                        game.getPlayerNumber(), game.getPlayTime(), game.getReleaseYear()));
    }
}

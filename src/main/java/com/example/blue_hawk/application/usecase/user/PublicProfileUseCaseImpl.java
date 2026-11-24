package com.example.blue_hawk.application.usecase.user;

import com.example.blue_hawk.application.usecase.auth.Access;

import com.example.blue_hawk.application.dto.auth.CurrentUser;
import com.example.blue_hawk.application.dto.user.UserProfileDtos.ProfileInfo;
import com.example.blue_hawk.application.dto.user.UserProfileDtos.ProfileStats;
import com.example.blue_hawk.application.dto.user.UserProfileDtos.PublicProfileView;
import com.example.blue_hawk.application.dto.user.UserProfileDtos.ShelfItem;
import com.example.blue_hawk.domain.entity.boardgame.Boardgame;
import com.example.blue_hawk.domain.entity.offer.Offer;
import com.example.blue_hawk.domain.entity.offer.OfferStatus;
import com.example.blue_hawk.domain.entity.user.User;
import com.example.blue_hawk.domain.entity.user.UserProfile;
import com.example.blue_hawk.domain.entity.userboardgame.UserBoardgame;
import com.example.blue_hawk.domain.repository.IBoardgameRepository;
import com.example.blue_hawk.domain.repository.IOfferRepository;
import com.example.blue_hawk.domain.repository.IOpenMatchRepository;
import com.example.blue_hawk.domain.repository.IUserBoardGameRepository;
import com.example.blue_hawk.domain.repository.IUserProfileRepository;
import com.example.blue_hawk.domain.repository.IUserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class PublicProfileUseCaseImpl implements PublicProfileUseCase {

    /** Quantas cópias/anúncios são lidos (uma estante real fica bem abaixo disso) */
    static final int MAX_ITEMS = 500;

    private final IUserRepository userRepository;
    private final IUserProfileRepository profileRepository;
    private final IUserBoardGameRepository userBoardGameRepository;
    private final IBoardgameRepository boardgameRepository;
    private final IOfferRepository offerRepository;
    private final IOpenMatchRepository openMatchRepository;

    public PublicProfileUseCaseImpl(IUserRepository userRepository,
                                    IUserProfileRepository profileRepository,
                                    IUserBoardGameRepository userBoardGameRepository,
                                    IBoardgameRepository boardgameRepository,
                                    IOfferRepository offerRepository,
                                    IOpenMatchRepository openMatchRepository) {
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
        this.userBoardGameRepository = userBoardGameRepository;
        this.boardgameRepository = boardgameRepository;
        this.offerRepository = offerRepository;
        this.openMatchRepository = openMatchRepository;
    }

    @Override
    public PublicProfileView get(String userId, CurrentUser currentUser) {
        Access.requireLogin(currentUser);
        UUID id = parseId(userId);
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        UserProfile profile = profileRepository.findByUserId(id).orElseGet(() -> UserProfile.defaults(id));

        boolean isMe = currentUser.isSelf(id);
        String role = user.getRole() != null ? user.getRole().name() : null;
        String avatarUrl = UserProfileUseCaseImpl.avatarUrl(profile);

        if (!isMe && !profile.isPublic()) {
            // Perfil privado: só o essencial para identificar a pessoa
            return new PublicProfileView(id.toString(), user.getName(), role, profile.getAvatarPreset(), avatarUrl,
                    false, false, null, null, null, profile.isAllowDirectMessages());
        }

        List<ShelfItem> shelf = shelf(id);
        int forSale = (int) offerRepository.findAll(id, null, OfferStatus.ACTIVE, 0, 1).getTotalElements();
        long played = openMatchRepository.countPlayedByBoardgame(id, Instant.now()).values().stream()
                .mapToLong(Long::longValue)
                .sum();

        ProfileInfo info = new ProfileInfo(profile.getBio(), profile.getCity(),
                profile.getExperienceLevel() != null ? profile.getExperienceLevel().name() : null,
                profile.getFavoriteGenres(),
                user.getCreatedAt() != null ? user.getCreatedAt().toString() : null);
        ProfileStats stats = new ProfileStats(shelf.size(), forSale, played);

        return new PublicProfileView(id.toString(), user.getName(), role, profile.getAvatarPreset(), avatarUrl,
                profile.isPublic(), isMe, info, stats, isMe || profile.isShowShelf() ? shelf : null,
                profile.isAllowDirectMessages());
    }

    /**
     * Cópias do usuário como a estante mostra: jogos do catálogo e os que ele mesmo cadastrou, cada uma com o
     * anúncio ativo (se houver).
     */
    private List<ShelfItem> shelf(UUID userId) {
        Map<UUID, Offer> activeByCopy = new HashMap<>();
        for (Offer offer : offerRepository.findAll(userId, null, OfferStatus.ACTIVE, 0, MAX_ITEMS).getContent()) {
            activeByCopy.putIfAbsent(offer.getUserBoardgameId(), offer);
        }

        Map<UUID, Optional<Boardgame>> games = new HashMap<>();
        List<ShelfItem> items = new ArrayList<>();
        for (UserBoardgame copy : userBoardGameRepository.findAll(userId, null, 0, MAX_ITEMS).getContent()) {
            Boardgame game = games.computeIfAbsent(copy.getBoardgameId(), boardgameRepository::findById)
                    .orElse(null);
            if (game == null || !game.isVisibleTo(userId, false)) continue;
            Offer offer = activeByCopy.get(copy.getId());
            items.add(new ShelfItem(
                    game.getId().toString(),
                    game.getName(),
                    game.getCategoryName(),
                    game.getCoverPreset(),
                    game.getCoverImageUpdatedAt() != null
                            ? "/boardgames/" + game.getId() + "/cover?v=" + game.getCoverImageUpdatedAt().toEpochMilli()
                            : null,
                    offer != null ? offer.getId().toString() : null,
                    offer != null ? offer.getPrice() : null));
        }
        return items;
    }

    private static UUID parseId(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
    }
}

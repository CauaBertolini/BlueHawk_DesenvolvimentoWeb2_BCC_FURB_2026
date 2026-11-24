package com.example.blue_hawk.application.usecase.user;

import com.example.blue_hawk.application.usecase.auth.Access;

import com.example.blue_hawk.application.dto.auth.CurrentUser;
import com.example.blue_hawk.application.dto.user.UserProfileDtos.MyProfileView;
import com.example.blue_hawk.application.dto.user.UserProfileDtos.UpdateMyProfileCommand;
import com.example.blue_hawk.application.usecase.boardgame.BoardgameCoverUseCaseImpl;
import com.example.blue_hawk.domain.entity.user.UserProfile;
import com.example.blue_hawk.domain.repository.IUserProfileRepository;
import com.example.blue_hawk.domain.repository.IUserProfileRepository.AvatarImage;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class UserProfileUseCaseImpl implements UserProfileUseCase {

    /** Limite da foto (o front reduz antes de enviar); o Spring também barra multipart acima disso */
    static final int MAX_AVATAR_BYTES = 3 * 1024 * 1024;
    static final int MAX_BIO = 500;
    static final int MAX_CITY = 100;
    static final int MAX_GENRES = 200;
    static final int MAX_PRESET = 40;
    /** Id de uma arte do site: livre, mas só minúsculas, dígitos e hífen (vai para classe CSS/URL no front) */
    private static final Pattern PRESET_PATTERN = Pattern.compile("^[a-z0-9-]+$");

    private final IUserProfileRepository profileRepository;

    public UserProfileUseCaseImpl(IUserProfileRepository profileRepository) {
        this.profileRepository = profileRepository;
    }

    @Override
    public MyProfileView getMine(CurrentUser currentUser) {
        UUID me = Access.requireUserId(currentUser);
        return view(load(me));
    }

    @Override
    @Transactional
    public MyProfileView updateMine(UpdateMyProfileCommand command, CurrentUser currentUser) {
        UUID me = Access.requireUserId(currentUser);
        if (command == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "body is required");
        }
        // Valida tudo antes de alterar qualquer coisa
        String bio = text(command.bio(), MAX_BIO, "bio");
        String city = text(command.city(), MAX_CITY, "city");
        String genres = text(command.favoriteGenres(), MAX_GENRES, "favoriteGenres");
        UserProfile.ExperienceLevel level = level(command.experienceLevel());
        String preset = preset(command.avatarPreset());

        UserProfile profile = load(me);
        boolean hadImage = profile.hasAvatarImage();
        profile.setBio(bio);
        profile.setCity(city);
        profile.setFavoriteGenres(genres);
        profile.setExperienceLevel(level);
        if (command.isPublic() != null) profile.setPublic(command.isPublic());
        if (command.showShelf() != null) profile.setShowShelf(command.showShelf());
        if (command.matchAlerts() != null) profile.setMatchAlerts(command.matchAlerts());
        if (command.offerAlerts() != null) profile.setOfferAlerts(command.offerAlerts());
        if (command.allowDirectMessages() != null) profile.setAllowDirectMessages(command.allowDirectMessages());
        profile.choosePreset(preset);

        UserProfile saved = save(profile, me);
        if (hadImage && !saved.hasAvatarImage()) {
            // Escolheu uma arte: a foto enviada é apagada
            profileRepository.saveAvatarImage(me, null);
        }
        return view(saved);
    }

    @Override
    @Transactional
    public MyProfileView uploadAvatar(byte[] data, CurrentUser currentUser) {
        UUID me = Access.requireUserId(currentUser);
        if (data == null || data.length == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "file is required");
        }
        if (data.length > MAX_AVATAR_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "file must be at most 3 MB");
        }
        // O tipo vem dos bytes, não do Content-Type informado pelo cliente
        String contentType = BoardgameCoverUseCaseImpl.detectImageType(data);
        if (contentType == null) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "file must be a JPEG, PNG or WebP image");
        }

        UserProfile profile = load(me);
        // Precisão de milissegundos: é o que vai na URL (?v=)
        profile.useImage(contentType, Instant.now().truncatedTo(ChronoUnit.MILLIS));
        UserProfile saved = save(profile, me);
        profileRepository.saveAvatarImage(me, data);
        return view(saved);
    }

    @Override
    @Transactional
    public MyProfileView removeAvatar(CurrentUser currentUser) {
        UUID me = Access.requireUserId(currentUser);
        UserProfile profile = load(me);
        if (!profile.isPersisted()) {
            // Nada gravado: continua nos padrões, sem criar linha
            return view(profile);
        }
        profile.clearAvatar();
        UserProfile saved = save(profile, me);
        profileRepository.saveAvatarImage(me, null);
        return view(saved);
    }

    @Override
    public AvatarImage getAvatar(String userId) {
        UUID id;
        try {
            id = UUID.fromString(userId);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Avatar not found");
        }
        return profileRepository.findAvatarImage(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Avatar not found"));
    }

    // ── auxiliares ──────────────────────────────────────────────────────────

    private UserProfile load(UUID userId) {
        return profileRepository.findByUserId(userId).orElseGet(() -> UserProfile.defaults(userId));
    }

    private UserProfile save(UserProfile profile, UUID by) {
        profile.touch(by, Instant.now());
        return profileRepository.save(profile);
    }

    static MyProfileView view(UserProfile p) {
        return new MyProfileView(p.getBio(), p.getCity(),
                p.getExperienceLevel() != null ? p.getExperienceLevel().name() : null,
                p.getFavoriteGenres(), p.isPublic(), p.isShowShelf(), p.isMatchAlerts(), p.isOfferAlerts(),
                p.isAllowDirectMessages(), p.getAvatarPreset(), avatarUrl(p));
    }

    /** URL versionada da foto (pode ficar em cache por muito tempo); null sem foto */
    static String avatarUrl(UserProfile p) {
        return p.hasAvatarImage()
                ? "/users/" + p.getUserId() + "/avatar?v=" + p.getAvatarUpdatedAt().toEpochMilli()
                : null;
    }

    /** Trim; vazio vira null; acima do limite da coluna é 400 */
    private static String text(String value, int max, String field) {
        if (value == null) return null;
        String trimmed = value.trim();
        if (trimmed.isEmpty()) return null;
        if (trimmed.length() > max) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    field + " must be at most " + max + " characters");
        }
        return trimmed;
    }

    private static UserProfile.ExperienceLevel level(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) return null;
        try {
            return UserProfile.ExperienceLevel.valueOf(trimmed.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "experienceLevel must be INICIANTE, INTERMEDIARIO or VETERANO");
        }
    }

    private static String preset(String value) {
        String preset = text(value, MAX_PRESET, "avatarPreset");
        if (preset != null && !PRESET_PATTERN.matcher(preset).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "avatarPreset must contain only lowercase letters, digits and hyphens");
        }
        return preset;
    }
}

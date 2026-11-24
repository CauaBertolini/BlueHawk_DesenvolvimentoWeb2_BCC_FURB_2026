package com.example.blue_hawk.application.usecase.auth;

import com.example.blue_hawk.application.dto.auth.CurrentUser;
import com.example.blue_hawk.domain.entity.match.Match;
import com.example.blue_hawk.domain.entity.offer.Offer;
import com.example.blue_hawk.domain.entity.userboardgame.UserBoardgame;
import com.example.blue_hawk.domain.repository.IUserBoardGameRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * Regras de permissão compartilhadas pelos casos de uso: quem está logado e quem é dono do quê.
 * Estante, anúncios e partidas pertencem ao dono da cópia ({@link UserBoardgame}); ADMIN pode tudo.
 */
public final class Access {

    private Access() {
    }

    /** 401 para visitante; devolve o usuário logado. */
    public static CurrentUser requireLogin(CurrentUser currentUser) {
        if (currentUser == null || !currentUser.authenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return currentUser;
    }

    /** 401 para visitante; devolve o id do usuário logado. */
    public static UUID requireUserId(CurrentUser currentUser) {
        return requireLogin(currentUser).userId();
    }

    /** 403 a menos que o usuário logado seja {@code ownerId} ou um ADMIN. */
    public static void requireSelfOrAdmin(UUID ownerId, CurrentUser currentUser, String message) {
        if (!requireLogin(currentUser).isSelfOrAdmin(ownerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, message);
        }
    }

    /** O usuário logado é o dono da cópia {@code copyId}? (cópia inexistente não tem dono) */
    public static boolean ownsCopy(IUserBoardGameRepository copies, UUID copyId, CurrentUser currentUser) {
        if (copyId == null || currentUser == null || !currentUser.authenticated()) {
            return false;
        }
        return copies.findById(copyId)
                .map(UserBoardgame::getUserId)
                .filter(currentUser::isSelf)
                .isPresent();
    }

    /** 403 a menos que o usuário logado seja o dono da cópia {@code copyId} ou um ADMIN. */
    public static void requireCopyOwner(IUserBoardGameRepository copies, UUID copyId, CurrentUser currentUser,
                                        String message) {
        CurrentUser user = requireLogin(currentUser);
        if (!user.admin() && !ownsCopy(copies, copyId, user)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, message);
        }
    }

    // ── regras por recurso ─────────────────────────────────────────────────

    /** Estante: cada um mexe só na própria (a cópia já carregada é de {@code currentUser}?) */
    public static void requireShelfOwner(UserBoardgame copy, CurrentUser currentUser) {
        requireSelfOrAdmin(copy.getUserId(), currentUser, "You can only use games from your own shelf");
    }

    /** Idem, a partir do id da cópia */
    public static void requireShelfOwner(IUserBoardGameRepository copies, UUID copyId, CurrentUser currentUser) {
        requireCopyOwner(copies, copyId, currentUser, "You can only use games from your own shelf");
    }

    /** Anúncio: só o dono da cópia anunciada altera ou apaga */
    public static void requireOfferOwner(Offer offer, IUserBoardGameRepository copies, CurrentUser currentUser) {
        requireCopyOwner(copies, offer.getUserBoardgameId(), currentUser, "You can only change your own offers");
    }

    /** O usuário logado é o anfitrião da partida (dono da cópia usada nela)? */
    public static boolean isMatchHost(Match match, IUserBoardGameRepository copies, CurrentUser currentUser) {
        return ownsCopy(copies, match.getUserBoardgameId(), currentUser);
    }

    /** Partida: só o anfitrião altera ou apaga */
    public static void requireMatchHost(Match match, IUserBoardGameRepository copies, CurrentUser currentUser) {
        requireCopyOwner(copies, match.getUserBoardgameId(), currentUser, "Only the match host can do this");
    }
}

package com.example.blue_hawk.application.usecase.matchParticipant;

import com.example.blue_hawk.application.dto.auth.CurrentUser;
import com.example.blue_hawk.application.usecase.auth.Access;
import com.example.blue_hawk.domain.entity.matchparticipant.MatchParticipant;
import com.example.blue_hawk.domain.repository.IMatchRepository;
import com.example.blue_hawk.domain.repository.IUserBoardGameRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/** Permissões das participações em partidas. */
final class MatchParticipantAccess {

    private MatchParticipantAccess() {
    }

    /** Cada um entra na partida por conta própria (ADMIN pode incluir qualquer pessoa); devolve o id do jogador */
    static UUID requireJoinAsSelf(String matchId, String userId, CurrentUser currentUser) {
        if (matchId == null || userId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "matchId and userId are required");
        }
        UUID playerId = UUID.fromString(userId);
        Access.requireSelfOrAdmin(playerId, currentUser, "You can only join a match as yourself");
        return playerId;
    }

    /** Alterar ou remover uma participação: o próprio jogador, o anfitrião da partida ou um ADMIN */
    static void requireCanManage(MatchParticipant participant, CurrentUser currentUser,
                                 IMatchRepository matches, IUserBoardGameRepository copies) {
        CurrentUser user = Access.requireLogin(currentUser);
        if (user.admin() || user.isSelf(participant.getUserId())) {
            return;
        }
        boolean host = matches.findById(participant.getMatchId())
                .filter(match -> Access.isMatchHost(match, copies, user))
                .isPresent();
        if (!host) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only the participant or the match host can do this");
        }
    }
}

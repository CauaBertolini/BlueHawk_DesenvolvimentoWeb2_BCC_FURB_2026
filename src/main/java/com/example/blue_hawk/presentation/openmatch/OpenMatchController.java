package com.example.blue_hawk.presentation.openmatch;

import com.example.blue_hawk.application.dto.auth.CurrentUser;
import com.example.blue_hawk.application.dto.openmatch.OpenMatchDtos.HostCommand;
import com.example.blue_hawk.application.dto.openmatch.OpenMatchDtos.ListQuery;
import com.example.blue_hawk.application.dto.openmatch.OpenMatchDtos.OpenMatchView;
import com.example.blue_hawk.application.dto.openmatch.OpenMatchDtos.RequestView;
import com.example.blue_hawk.application.usecase.openmatch.OpenMatchUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Matchmaking ("Jogue"). A listagem é pública; hospedar, pedir para entrar e decidir pedidos exigem login.
 */
@RestController
public class OpenMatchController {

    public record HostRequest(String userBoardgameId, String city, String scheduledAt, Integer maxPlayers,
                              String notes) {
    }

    public record JoinRequest(String message) {
    }

    public record DecisionRequest(String reply) {
    }

    public record GameResponse(String id, String name, String category, String coverPreset, String coverImageUrl,
                               Integer playerNumber, Integer playTime) {
    }

    public record OpenMatchResponse(String id, String status, String city, String scheduledAt, int maxPlayers,
                                    int players, int seatsLeft, Integer pendingRequests, String notes,
                                    Object host, GameResponse game, boolean isHost, Object myRequest,
                                    String cancelReason) {
    }

    /** Motivo do cancelamento, obrigatório (10 a 300 caracteres) */
    public record CancelRequest(String reason) {
    }

    private final OpenMatchUseCase openMatchUseCase;

    public OpenMatchController(OpenMatchUseCase openMatchUseCase) {
        this.openMatchUseCase = openMatchUseCase;
    }

    /** {@code mine=true}: partidas que o usuário hospeda ou pediu para entrar (exige login) */
    @GetMapping("/open-matches")
    public List<OpenMatchResponse> list(@RequestParam(name = "city", required = false) String city,
                                        @RequestParam(name = "boardgameId", required = false) String boardgameId,
                                        @RequestParam(name = "mine", defaultValue = "false") boolean mine,
                                        CurrentUser currentUser) {
        return openMatchUseCase.list(new ListQuery(city, boardgameId, mine, currentUser)).stream()
                .map(OpenMatchController::toResponse)
                .toList();
    }

    /** Partidas realizadas pelo usuário, por jogo (estatísticas da estante). Exige login. */
    @GetMapping("/open-matches/stats/played")
    public List<OpenMatchUseCase.PlayedView> playedByMe(CurrentUser currentUser) {
        return openMatchUseCase.playedByMe(currentUser);
    }

    @GetMapping("/open-matches/{id}")
    public OpenMatchResponse get(@PathVariable String id, CurrentUser currentUser) {
        return toResponse(openMatchUseCase.get(id, currentUser));
    }

    @PostMapping("/open-matches")
    public ResponseEntity<OpenMatchResponse> host(@RequestBody HostRequest request, CurrentUser currentUser) {
        OpenMatchView view = openMatchUseCase.host(new HostCommand(request.userBoardgameId(), request.city(),
                request.scheduledAt(), request.maxPlayers(), request.notes(), currentUser));
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(view));
    }

    /** O host cancela a partida. Corpo {@code { "reason": "..." }} obrigatório: o motivo vai para os jogadores. */
    @DeleteMapping("/open-matches/{id}")
    public OpenMatchResponse cancel(@PathVariable String id,
                                    @RequestBody(required = false) CancelRequest request,
                                    CurrentUser currentUser) {
        return toResponse(openMatchUseCase.cancel(id, request != null ? request.reason() : null, currentUser));
    }

    @PostMapping("/open-matches/{id}/requests")
    public ResponseEntity<OpenMatchResponse> requestToJoin(@PathVariable String id,
                                                           @RequestBody(required = false) JoinRequest request,
                                                           CurrentUser currentUser) {
        OpenMatchView view = openMatchUseCase.requestToJoin(id, request != null ? request.message() : null,
                currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(view));
    }

    /** Quem pediu desiste */
    @DeleteMapping("/open-matches/{id}/requests/mine")
    public OpenMatchResponse cancelMyRequest(@PathVariable String id, CurrentUser currentUser) {
        return toResponse(openMatchUseCase.cancelMyRequest(id, currentUser));
    }

    /** Só o host */
    @GetMapping("/open-matches/{id}/requests")
    public List<RequestView> listRequests(@PathVariable String id, CurrentUser currentUser) {
        return openMatchUseCase.listRequests(id, currentUser);
    }

    @PostMapping("/open-match-requests/{requestId}/accept")
    public RequestView accept(@PathVariable String requestId, @RequestBody(required = false) DecisionRequest request,
                              CurrentUser currentUser) {
        return openMatchUseCase.decide(requestId, true, request != null ? request.reply() : null, currentUser);
    }

    @PostMapping("/open-match-requests/{requestId}/reject")
    public RequestView reject(@PathVariable String requestId, @RequestBody(required = false) DecisionRequest request,
                              CurrentUser currentUser) {
        return openMatchUseCase.decide(requestId, false, request != null ? request.reply() : null, currentUser);
    }

    private static OpenMatchResponse toResponse(OpenMatchView v) {
        GameResponse game = v.game() == null ? null : new GameResponse(v.game().id(), v.game().name(),
                v.game().category(), v.game().coverPreset(),
                v.game().coverImageVersion() != null
                        ? "/boardgames/" + v.game().id() + "/cover?v=" + v.game().coverImageVersion()
                        : null,
                v.game().playerNumber(), v.game().playTime());
        return new OpenMatchResponse(v.id(), v.status(), v.city(), v.scheduledAt(), v.maxPlayers(), v.players(),
                v.seatsLeft(), v.pendingRequests(), v.notes(), v.host(), game, v.isHost(), v.myRequest(),
                v.cancelReason());
    }
}

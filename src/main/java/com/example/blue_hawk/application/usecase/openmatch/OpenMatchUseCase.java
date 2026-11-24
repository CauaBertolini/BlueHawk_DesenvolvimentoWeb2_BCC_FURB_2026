package com.example.blue_hawk.application.usecase.openmatch;

import com.example.blue_hawk.application.dto.auth.CurrentUser;
import com.example.blue_hawk.application.dto.openmatch.OpenMatchDtos.HostCommand;
import com.example.blue_hawk.application.dto.openmatch.OpenMatchDtos.ListQuery;
import com.example.blue_hawk.application.dto.openmatch.OpenMatchDtos.OpenMatchView;
import com.example.blue_hawk.application.dto.openmatch.OpenMatchDtos.RequestView;

import java.util.List;

/** Matchmaking: hospedar partidas, pedir para entrar e o host aceitar ou recusar (estilo carona). */
public interface OpenMatchUseCase {

    List<OpenMatchView> list(ListQuery query);

    OpenMatchView get(String id, CurrentUser currentUser);

    OpenMatchView host(HostCommand command);

    int CANCEL_REASON_MIN = 10;
    int CANCEL_REASON_MAX = 300;

    /**
     * Só o host. {@code reason} obrigatório (com trim, de 10 a 300 caracteres), mostrado aos jogadores. Cancelar
     * de novo uma partida já cancelada não troca o motivo nem notifica outra vez.
     */
    OpenMatchView cancel(String id, String reason, CurrentUser currentUser);

    OpenMatchView requestToJoin(String id, String message, CurrentUser currentUser);

    /** Quem pediu desiste (pendente ou já aceito) */
    OpenMatchView cancelMyRequest(String id, CurrentUser currentUser);

    /** Só o host: pedidos da partida, com nome e mensagem de quem pediu */
    List<RequestView> listRequests(String id, CurrentUser currentUser);

    /** Só o host */
    RequestView decide(String requestId, boolean accept, String reply, CurrentUser currentUser);

    /** Partidas realizadas pelo usuário, por jogo: base das estatísticas da estante */
    record PlayedView(String boardgameId, long plays) {
    }

    /**
     * Conta as partidas do Jogue já realizadas (data passada, não canceladas) em que o usuário foi host ou teve o
     * pedido aceito. É a única fonte de "partidas jogadas" da estante.
     */
    List<PlayedView> playedByMe(CurrentUser currentUser);
}

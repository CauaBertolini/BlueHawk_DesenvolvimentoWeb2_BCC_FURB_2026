package com.example.blue_hawk.application.port;

import java.util.UUID;

/** Controle das conexões de tempo real abertas (implementado na infraestrutura via WebSocket em {@code /ws}). */
public interface RealtimeConnections {

    /** Derruba as conexões abertas do usuário (conta excluída): ele não deve mais receber eventos */
    void disconnectUser(UUID userId);
}

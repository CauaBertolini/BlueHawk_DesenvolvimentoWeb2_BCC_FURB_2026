package com.example.blue_hawk.domain.repository;

import com.example.blue_hawk.domain.entity.openmatch.OpenMatchMessage;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IOpenMatchMessageRepository {

    OpenMatchMessage save(OpenMatchMessage message);

    /**
     * Chat da mesa: as {@code limit} mensagens mais recentes, em ordem cronológica.
     * {@code after} (opcional): só as estritamente posteriores.
     */
    List<OpenMatchMessage> findTable(UUID openMatchId, Instant after, int limit);

    /** Conversa direta entre {@code userA} e {@code userB} (nos dois sentidos), como em {@link #findTable} */
    List<OpenMatchMessage> findDirect(UUID openMatchId, UUID userA, UUID userB, Instant after, int limit);

    /** Última mensagem da mesa */
    Optional<OpenMatchMessage> findLastTable(UUID openMatchId);

    /** Última mensagem da conversa direta entre os dois usuários */
    Optional<OpenMatchMessage> findLastDirect(UUID openMatchId, UUID userA, UUID userB);
}

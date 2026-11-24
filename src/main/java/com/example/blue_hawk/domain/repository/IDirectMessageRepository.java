package com.example.blue_hawk.domain.repository;

import com.example.blue_hawk.domain.entity.direct.DirectMessage;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IDirectMessageRepository {

    DirectMessage save(DirectMessage message);

    /**
     * As {@code limit} mensagens mais recentes da conversa, em ordem cronológica.
     * {@code after} (opcional): só as estritamente posteriores.
     */
    List<DirectMessage> find(UUID conversationId, Instant after, int limit);

    /** Última mensagem da conversa */
    Optional<DirectMessage> findLast(UUID conversationId);
}

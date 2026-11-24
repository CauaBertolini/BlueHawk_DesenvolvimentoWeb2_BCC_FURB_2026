package com.example.blue_hawk.domain.repository;

import com.example.blue_hawk.domain.entity.direct.DirectConversation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IDirectConversationRepository {

    DirectConversation save(DirectConversation conversation);

    Optional<DirectConversation> findById(UUID id);

    /** A conversa entre os dois usuários, em qualquer ordem */
    Optional<DirectConversation> findBetween(UUID userA, UUID userB);

    /** Conversas em que o usuário participa */
    List<DirectConversation> findInvolving(UUID userId);
}

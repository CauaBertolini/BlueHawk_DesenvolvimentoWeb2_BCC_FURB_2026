-- Chat das partidas do Jogue (mesa e conversa direta host <-> participante) e notificações dos usuários.
-- As duas tabelas têm auditoria completa: created_at/created_by e updated_at/updated_by.

CREATE TABLE open_match_message (
    id            CHAR(36)      NOT NULL,
    open_match_id CHAR(36)      NOT NULL,
    sender_id     CHAR(36)      NOT NULL,
    -- NULL = mensagem para a mesa toda; preenchido = conversa direta (sempre entre o host e um participante)
    recipient_id  CHAR(36)      NULL,
    body          VARCHAR(1000) NOT NULL,
    created_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by    CHAR(36)      NULL,
    updated_at    TIMESTAMP     NULL,
    updated_by    CHAR(36)      NULL,
    CONSTRAINT open_match_message_pk PRIMARY KEY (id),
    CONSTRAINT open_match_message_match FOREIGN KEY (open_match_id) REFERENCES open_match (id) ON DELETE CASCADE,
    CONSTRAINT open_match_message_sender FOREIGN KEY (sender_id) REFERENCES "user" (id) ON DELETE CASCADE,
    CONSTRAINT open_match_message_recipient FOREIGN KEY (recipient_id) REFERENCES "user" (id) ON DELETE CASCADE,
    CONSTRAINT open_match_message_created_by FOREIGN KEY (created_by) REFERENCES "user" (id) ON DELETE SET NULL,
    CONSTRAINT open_match_message_updated_by FOREIGN KEY (updated_by) REFERENCES "user" (id) ON DELETE SET NULL
);

-- Leitura de uma conversa (mesa: recipient_id nulo) em ordem cronológica
CREATE INDEX open_match_message_thread_idx ON open_match_message (open_match_id, recipient_id, created_at);
-- Conversas diretas: mensagens de um remetente para um destinatário na partida
CREATE INDEX open_match_message_direct_idx ON open_match_message (open_match_id, sender_id, recipient_id);

CREATE TABLE notification (
    id             CHAR(36)    NOT NULL,
    -- Destinatário
    user_id        CHAR(36)    NOT NULL,
    type           VARCHAR(40) NOT NULL,
    -- Quem causou (na mensagem agregada: o último remetente); NULL = sistema ou usuário removido
    actor_id       CHAR(36)    NULL,
    open_match_id  CHAR(36)    NULL,
    board_game_id  CHAR(36)    NULL,
    -- Só em MATCH_MESSAGE: a outra pessoa da conversa direta (NULL = chat da mesa)
    thread_user_id CHAR(36)    NULL,
    -- Mensagens agregadas numa notificação não lida
    count          INT         NOT NULL DEFAULT 1,
    read_at        TIMESTAMP   NULL,
    created_at     TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by     CHAR(36)    NULL,
    -- Última atividade (nova mensagem agregada); marcar como lida usa só read_at
    updated_at     TIMESTAMP   NULL,
    updated_by     CHAR(36)    NULL,
    CONSTRAINT notification_pk PRIMARY KEY (id),
    CONSTRAINT notification_type_chk CHECK (type IN ('JOIN_REQUEST_RECEIVED', 'JOIN_REQUEST_WITHDRAWN',
        'JOIN_REQUEST_ACCEPTED', 'JOIN_REQUEST_REJECTED', 'MATCH_CANCELLED', 'MATCH_MESSAGE',
        'BOARDGAME_APPROVED', 'BOARDGAME_REJECTED')),
    CONSTRAINT notification_count_chk CHECK (count >= 1),
    CONSTRAINT notification_user FOREIGN KEY (user_id) REFERENCES "user" (id) ON DELETE CASCADE,
    CONSTRAINT notification_actor FOREIGN KEY (actor_id) REFERENCES "user" (id) ON DELETE SET NULL,
    CONSTRAINT notification_open_match FOREIGN KEY (open_match_id) REFERENCES open_match (id) ON DELETE CASCADE,
    CONSTRAINT notification_board_game FOREIGN KEY (board_game_id) REFERENCES board_game (id) ON DELETE CASCADE,
    CONSTRAINT notification_thread_user FOREIGN KEY (thread_user_id) REFERENCES "user" (id) ON DELETE CASCADE,
    CONSTRAINT notification_created_by FOREIGN KEY (created_by) REFERENCES "user" (id) ON DELETE SET NULL,
    CONSTRAINT notification_updated_by FOREIGN KEY (updated_by) REFERENCES "user" (id) ON DELETE SET NULL
);

CREATE INDEX notification_user_idx ON notification (user_id, read_at, created_at DESC);

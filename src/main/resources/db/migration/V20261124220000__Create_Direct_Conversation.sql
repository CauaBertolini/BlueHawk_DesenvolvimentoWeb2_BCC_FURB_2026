-- Mensagens diretas entre usuários do site (fora das partidas e das negociações), a preferência de aceitar
-- mensagens diretas no perfil e a notificação DIRECT_MESSAGE. As tabelas novas têm auditoria completa:
-- created_at/created_by e updated_at/updated_by.

CREATE TABLE direct_conversation (
    id              CHAR(36)  NOT NULL,
    -- O par é gravado em ordem (user_a_id < user_b_id): uma conversa por par, nos dois sentidos
    user_a_id       CHAR(36)  NOT NULL,
    user_b_id       CHAR(36)  NOT NULL,
    -- Última mensagem (NULL = conversa ainda vazia)
    last_message_at TIMESTAMP NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      CHAR(36)  NULL,
    updated_at      TIMESTAMP NULL,
    updated_by      CHAR(36)  NULL,
    CONSTRAINT direct_conversation_pk PRIMARY KEY (id),
    CONSTRAINT direct_conversation_pair_uk UNIQUE (user_a_id, user_b_id),
    CONSTRAINT direct_conversation_distinct_chk CHECK (user_a_id <> user_b_id),
    CONSTRAINT direct_conversation_order_chk CHECK (user_a_id < user_b_id),
    CONSTRAINT direct_conversation_user_a FOREIGN KEY (user_a_id) REFERENCES "user" (id) ON DELETE CASCADE,
    CONSTRAINT direct_conversation_user_b FOREIGN KEY (user_b_id) REFERENCES "user" (id) ON DELETE CASCADE,
    CONSTRAINT direct_conversation_created_by FOREIGN KEY (created_by) REFERENCES "user" (id) ON DELETE SET NULL,
    CONSTRAINT direct_conversation_updated_by FOREIGN KEY (updated_by) REFERENCES "user" (id) ON DELETE SET NULL
);

-- Conversas em que o usuário é o segundo do par (o primeiro já usa o índice da UNIQUE)
CREATE INDEX direct_conversation_user_b_idx ON direct_conversation (user_b_id);

CREATE TABLE direct_message (
    id              CHAR(36)      NOT NULL,
    conversation_id CHAR(36)      NOT NULL,
    sender_id       CHAR(36)      NOT NULL,
    body            VARCHAR(1000) NOT NULL,
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      CHAR(36)      NULL,
    updated_at      TIMESTAMP     NULL,
    updated_by      CHAR(36)      NULL,
    CONSTRAINT direct_message_pk PRIMARY KEY (id),
    CONSTRAINT direct_message_conversation FOREIGN KEY (conversation_id)
        REFERENCES direct_conversation (id) ON DELETE CASCADE,
    CONSTRAINT direct_message_sender FOREIGN KEY (sender_id) REFERENCES "user" (id) ON DELETE CASCADE,
    CONSTRAINT direct_message_created_by FOREIGN KEY (created_by) REFERENCES "user" (id) ON DELETE SET NULL,
    CONSTRAINT direct_message_updated_by FOREIGN KEY (updated_by) REFERENCES "user" (id) ON DELETE SET NULL
);

-- Leitura da conversa em ordem cronológica
CREATE INDEX direct_message_thread_idx ON direct_message (conversation_id, created_at);

-- Preferência: aceitar mensagens diretas de qualquer usuário (FALSE = ninguém pode começar uma conversa nova
-- nem escrever nas existentes)
ALTER TABLE user_profile ADD COLUMN allow_direct_messages BOOLEAN NOT NULL DEFAULT TRUE;

-- Notificação de mensagem direta (agregada como MATCH_MESSAGE)
ALTER TABLE notification ADD COLUMN direct_conversation_id CHAR(36) NULL;
ALTER TABLE notification ADD CONSTRAINT notification_direct_conversation FOREIGN KEY (direct_conversation_id)
    REFERENCES direct_conversation (id) ON DELETE CASCADE;
CREATE INDEX notification_direct_conversation_idx ON notification (direct_conversation_id);

ALTER TABLE notification DROP CONSTRAINT notification_type_chk;
ALTER TABLE notification ADD CONSTRAINT notification_type_chk CHECK (type IN ('JOIN_REQUEST_RECEIVED',
    'JOIN_REQUEST_WITHDRAWN', 'JOIN_REQUEST_ACCEPTED', 'JOIN_REQUEST_REJECTED', 'MATCH_CANCELLED', 'MATCH_MESSAGE',
    'BOARDGAME_APPROVED', 'BOARDGAME_REJECTED', 'OFFER_MESSAGE', 'OFFER_ACCEPTED', 'DIRECT_MESSAGE'));

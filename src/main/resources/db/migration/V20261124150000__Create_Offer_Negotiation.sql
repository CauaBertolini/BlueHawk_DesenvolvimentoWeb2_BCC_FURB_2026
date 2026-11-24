-- Negociação dos anúncios da Loja: conversa entre o comprador e o vendedor (dono da cópia anunciada), com
-- propostas de preço que o vendedor pode aceitar. As duas tabelas têm auditoria completa:
-- created_at/created_by e updated_at/updated_by.

CREATE TABLE offer_negotiation (
    id           CHAR(36)       NOT NULL,
    offer_id     CHAR(36)       NOT NULL,
    buyer_id     CHAR(36)       NOT NULL,
    -- OPEN: em conversa; ACCEPTED: o vendedor aceitou uma proposta; CLOSED: o anúncio foi vendido para outra pessoa
    status       VARCHAR(20)    NOT NULL DEFAULT 'OPEN',
    -- Preço da proposta aceita (só em ACCEPTED)
    agreed_price DECIMAL(10, 2) NULL,
    created_at   TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by   CHAR(36)       NULL,
    updated_at   TIMESTAMP      NULL,
    updated_by   CHAR(36)       NULL,
    CONSTRAINT offer_negotiation_pk PRIMARY KEY (id),
    CONSTRAINT offer_negotiation_status_chk CHECK (status IN ('OPEN', 'ACCEPTED', 'CLOSED')),
    -- Uma negociação por comprador por anúncio (começar de novo reaproveita a mesma)
    CONSTRAINT offer_negotiation_offer_buyer_uk UNIQUE (offer_id, buyer_id),
    CONSTRAINT offer_negotiation_offer FOREIGN KEY (offer_id) REFERENCES offer (id) ON DELETE CASCADE,
    CONSTRAINT offer_negotiation_buyer FOREIGN KEY (buyer_id) REFERENCES "user" (id) ON DELETE CASCADE,
    CONSTRAINT offer_negotiation_created_by FOREIGN KEY (created_by) REFERENCES "user" (id) ON DELETE SET NULL,
    CONSTRAINT offer_negotiation_updated_by FOREIGN KEY (updated_by) REFERENCES "user" (id) ON DELETE SET NULL
);

CREATE INDEX offer_negotiation_buyer_idx ON offer_negotiation (buyer_id);

CREATE TABLE offer_negotiation_message (
    id             CHAR(36)       NOT NULL,
    negotiation_id CHAR(36)       NOT NULL,
    sender_id      CHAR(36)       NOT NULL,
    body           VARCHAR(1000)  NOT NULL,
    -- Proposta de preço opcional junto com a mensagem
    proposed_price DECIMAL(10, 2) NULL,
    created_at     TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by     CHAR(36)       NULL,
    updated_at     TIMESTAMP      NULL,
    updated_by     CHAR(36)       NULL,
    CONSTRAINT offer_negotiation_message_pk PRIMARY KEY (id),
    CONSTRAINT offer_negotiation_message_price_chk CHECK (proposed_price IS NULL OR proposed_price > 0),
    CONSTRAINT offer_negotiation_message_negotiation FOREIGN KEY (negotiation_id)
        REFERENCES offer_negotiation (id) ON DELETE CASCADE,
    CONSTRAINT offer_negotiation_message_sender FOREIGN KEY (sender_id) REFERENCES "user" (id) ON DELETE CASCADE,
    CONSTRAINT offer_negotiation_message_created_by FOREIGN KEY (created_by) REFERENCES "user" (id) ON DELETE SET NULL,
    CONSTRAINT offer_negotiation_message_updated_by FOREIGN KEY (updated_by) REFERENCES "user" (id) ON DELETE SET NULL
);

-- Leitura da conversa em ordem cronológica
CREATE INDEX offer_negotiation_message_thread_idx ON offer_negotiation_message (negotiation_id, created_at);

-- Notificações das negociações: mensagem nova (agregada como MATCH_MESSAGE) e proposta aceita
ALTER TABLE notification ADD COLUMN negotiation_id CHAR(36) NULL;
ALTER TABLE notification ADD CONSTRAINT notification_negotiation FOREIGN KEY (negotiation_id)
    REFERENCES offer_negotiation (id) ON DELETE CASCADE;
CREATE INDEX notification_negotiation_idx ON notification (negotiation_id);

ALTER TABLE notification DROP CONSTRAINT notification_type_chk;
ALTER TABLE notification ADD CONSTRAINT notification_type_chk CHECK (type IN ('JOIN_REQUEST_RECEIVED',
    'JOIN_REQUEST_WITHDRAWN', 'JOIN_REQUEST_ACCEPTED', 'JOIN_REQUEST_REJECTED', 'MATCH_CANCELLED', 'MATCH_MESSAGE',
    'BOARDGAME_APPROVED', 'BOARDGAME_REJECTED', 'OFFER_MESSAGE', 'OFFER_ACCEPTED'));

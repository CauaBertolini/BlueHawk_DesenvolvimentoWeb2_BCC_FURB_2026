-- Matchmaking ("Jogue"): partidas anunciadas por um host e pedidos de entrada com aprovação.
-- Separado de match/match_user, que continuam sendo o registro de partidas JÁ jogadas (estatísticas da estante).

CREATE TABLE open_match (
    id                 CHAR(36)     NOT NULL,
    host_id            CHAR(36)     NOT NULL,
    user_board_game_id CHAR(36)     NOT NULL,
    city               VARCHAR(100) NOT NULL,
    scheduled_at       TIMESTAMP    NOT NULL,
    -- Total de jogadores na mesa, contando o host (limitado ao máximo do jogo)
    max_players        INT          NOT NULL,
    notes              VARCHAR(500) NULL,
    status             VARCHAR(20)  NOT NULL DEFAULT 'OPEN',
    created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT open_match_pk PRIMARY KEY (id),
    CONSTRAINT open_match_status_chk CHECK (status IN ('OPEN', 'CANCELLED')),
    CONSTRAINT open_match_max_players_chk CHECK (max_players >= 2),
    CONSTRAINT open_match_host FOREIGN KEY (host_id) REFERENCES "user" (id) ON DELETE CASCADE,
    CONSTRAINT open_match_user_board_game FOREIGN KEY (user_board_game_id)
        REFERENCES user_board_game (id) ON DELETE CASCADE
);

CREATE INDEX open_match_status_scheduled_idx ON open_match (status, scheduled_at);
CREATE INDEX open_match_city_idx ON open_match (LOWER(city));

CREATE TABLE open_match_request (
    id            CHAR(36)     NOT NULL,
    open_match_id CHAR(36)     NOT NULL,
    user_id       CHAR(36)     NOT NULL,
    -- Mensagem para o host (estilo carona: "posso levar salgadinhos")
    message       VARCHAR(500) NOT NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    host_reply    VARCHAR(500) NULL,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    decided_at    TIMESTAMP    NULL,
    CONSTRAINT open_match_request_pk PRIMARY KEY (id),
    CONSTRAINT open_match_request_status_chk CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED', 'CANCELLED')),
    -- Um pedido por pessoa por partida (um pedido cancelado pode ser reenviado: a linha é reaproveitada)
    CONSTRAINT open_match_request_uk UNIQUE (open_match_id, user_id),
    CONSTRAINT open_match_request_match FOREIGN KEY (open_match_id) REFERENCES open_match (id) ON DELETE CASCADE,
    CONSTRAINT open_match_request_user FOREIGN KEY (user_id) REFERENCES "user" (id) ON DELETE CASCADE
);

CREATE INDEX open_match_request_user_idx ON open_match_request (user_id);

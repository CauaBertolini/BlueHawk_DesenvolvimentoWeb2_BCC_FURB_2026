-- Curtidas de jogos da Wiki: uma por usuário por jogo, e o ranking "Mais curtidos".
CREATE TABLE board_game_like (
    board_game_id CHAR(36)  NOT NULL,
    user_id       CHAR(36)  NOT NULL,
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT board_game_like_pk PRIMARY KEY (board_game_id, user_id),
    CONSTRAINT board_game_like_board_game FOREIGN KEY (board_game_id) REFERENCES board_game (id) ON DELETE CASCADE,
    CONSTRAINT board_game_like_user FOREIGN KEY (user_id) REFERENCES "user" (id) ON DELETE CASCADE
);

CREATE INDEX board_game_like_user_idx ON board_game_like (user_id);

-- Contador desnormalizado: o ranking ordena por ele sem contar curtidas a cada consulta.
-- Só é alterado junto com board_game_like (incremento/decremento na mesma transação).
ALTER TABLE board_game ADD COLUMN like_count INT NOT NULL DEFAULT 0;
ALTER TABLE board_game ADD CONSTRAINT board_game_like_count_chk CHECK (like_count >= 0);
CREATE INDEX board_game_like_count_idx ON board_game (approval_status, like_count DESC);

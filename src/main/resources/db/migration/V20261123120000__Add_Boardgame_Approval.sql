-- Aprovacao de boardgames: jogos cadastrados por usuarios comuns entram como PENDING e so aparecem
-- na wiki depois que um ADMIN aprova. Os jogos que ja existem continuam visiveis (APPROVED).
ALTER TABLE board_game
    ADD COLUMN approval_status VARCHAR(20) NOT NULL DEFAULT 'APPROVED',
    ADD COLUMN submitted_by    CHAR(36)     NULL,
    ADD COLUMN review_note     VARCHAR(500) NULL;

ALTER TABLE board_game
    ADD CONSTRAINT board_game_approval_status_chk CHECK (approval_status IN ('PENDING', 'APPROVED', 'REJECTED'));

-- Daqui pra frente a aplicacao sempre informa o status; o default so serviu para os registros existentes.
ALTER TABLE board_game ALTER COLUMN approval_status SET DEFAULT 'PENDING';

-- Se o autor for removido, o jogo continua existindo, apenas sem autor.
ALTER TABLE board_game ADD CONSTRAINT board_game_submitted_by_user FOREIGN KEY (submitted_by)
    REFERENCES "user" (id) ON DELETE SET NULL;

CREATE INDEX board_game_approval_status_idx ON board_game (approval_status);

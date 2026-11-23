-- Categorias de boardgame: tabela própria, escolhida no cadastro do jogo.
CREATE TABLE category (
    id   CHAR(36)    NOT NULL,
    name VARCHAR(60) NOT NULL,
    CONSTRAINT category_pk PRIMARY KEY (id),
    CONSTRAINT category_name_uk UNIQUE (name)
);

-- Ids fixos: o front e os dados de teste podem referenciar estas categorias com segurança.
INSERT INTO category (id, name) VALUES
    ('0b6e4c1a-0001-4a6e-9c1a-000000000001', 'Estratégia'),
    ('0b6e4c1a-0001-4a6e-9c1a-000000000002', 'Cooperativo'),
    ('0b6e4c1a-0001-4a6e-9c1a-000000000003', 'Família'),
    ('0b6e4c1a-0001-4a6e-9c1a-000000000004', 'Party'),
    ('0b6e4c1a-0001-4a6e-9c1a-000000000005', 'Cartas'),
    ('0b6e4c1a-0001-4a6e-9c1a-000000000006', 'Abstrato');

-- Jogos antigos ficam sem categoria (NULL); novos cadastros exigem uma (validado na aplicação).
ALTER TABLE board_game ADD COLUMN category_id CHAR(36) NULL;
ALTER TABLE board_game ADD CONSTRAINT board_game_category FOREIGN KEY (category_id) REFERENCES category (id);

-- Capa: uma das artes do site (cover_preset) OU uma foto enviada (tabela board_game_cover).
-- NULL nos dois = arte automática, derivada do id do jogo.
ALTER TABLE board_game ADD COLUMN cover_preset VARCHAR(20) NULL;
-- Quando a foto foi enviada (NULL = sem foto). Evita join nas listagens e versiona a URL da imagem.
ALTER TABLE board_game ADD COLUMN cover_image_updated_at TIMESTAMP NULL;

-- Foto fica numa tabela à parte para as listagens não carregarem os bytes da imagem.
CREATE TABLE board_game_cover (
    board_game_id CHAR(36)    NOT NULL,
    content_type  VARCHAR(50) NOT NULL,
    data          BYTEA       NOT NULL,
    updated_at    TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT board_game_cover_pk PRIMARY KEY (board_game_id),
    CONSTRAINT board_game_cover_board_game FOREIGN KEY (board_game_id) REFERENCES board_game (id) ON DELETE CASCADE
);

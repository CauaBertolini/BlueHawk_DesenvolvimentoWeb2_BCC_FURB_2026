-- Jogos cadastrados antes de as categorias existirem ficaram com category_id NULL, e por isso as páginas de
-- categoria da Wiki e da Loja vinham vazias em produção. Esta migração dá uma categoria a cada um deles.
--
-- Só mexe em jogo que ainda está sem categoria e que tem exatamente um destes nomes: em outros bancos
-- (desenvolvimento, testes) nada muda. A categoria pode ser trocada depois pela edição do jogo.
--
-- Ids fixos das categorias (V20261123150000): 1 Estratégia, 2 Cooperativo, 3 Família, 4 Party, 5 Cartas, 6 Abstrato.
UPDATE board_game AS bg
SET category_id = m.category_id,
    updated_at  = CURRENT_TIMESTAMP
FROM (VALUES
    ('A Guilda dos Vidreiros',       '0b6e4c1a-0001-4a6e-9c1a-000000000001'),
    ('Caravanas do Sol',             '0b6e4c1a-0001-4a6e-9c1a-000000000001'),
    ('Catan',                        '0b6e4c1a-0001-4a6e-9c1a-000000000001'),
    ('Cidades de Areia',             '0b6e4c1a-0001-4a6e-9c1a-000000000001'),
    ('Expedição Abissal',            '0b6e4c1a-0001-4a6e-9c1a-000000000001'),
    ('Marés de Ferro',               '0b6e4c1a-0001-4a6e-9c1a-000000000001'),
    ('Minas de Quartzo',             '0b6e4c1a-0001-4a6e-9c1a-000000000001'),
    ('Noite de Campanha',            '0b6e4c1a-0001-4a6e-9c1a-000000000001'),
    ('Observatório Perdido',         '0b6e4c1a-0001-4a6e-9c1a-000000000001'),
    ('Trilhos do Planalto',          '0b6e4c1a-0001-4a6e-9c1a-000000000001'),
    ('Vinhedo das Colinas',          '0b6e4c1a-0001-4a6e-9c1a-000000000001'),
    ('Ilha das Tartarugas',          '0b6e4c1a-0001-4a6e-9c1a-000000000002'),
    ('O Último Farol',               '0b6e4c1a-0001-4a6e-9c1a-000000000002'),
    ('Ponte de Cordas',              '0b6e4c1a-0001-4a6e-9c1a-000000000002'),
    ('Cartógrafos da Névoa',         '0b6e4c1a-0001-4a6e-9c1a-000000000003'),
    ('Corrida de Caramujos',         '0b6e4c1a-0001-4a6e-9c1a-000000000003'),
    ('Feira das Especiarias',        '0b6e4c1a-0001-4a6e-9c1a-000000000003'),
    ('Jardim Suspenso',              '0b6e4c1a-0001-4a6e-9c1a-000000000003'),
    ('Mercado Flutuante',            '0b6e4c1a-0001-4a6e-9c1a-000000000003'),
    ('O Baú do Alquimista',          '0b6e4c1a-0001-4a6e-9c1a-000000000003'),
    ('Pescadores da Aurora',         '0b6e4c1a-0001-4a6e-9c1a-000000000003'),
    ('Vale dos Corvos',              '0b6e4c1a-0001-4a6e-9c1a-000000000003'),
    ('O Conselho das Aves',          '0b6e4c1a-0001-4a6e-9c1a-000000000004'),
    ('Sopa de Pedra',                '0b6e4c1a-0001-4a6e-9c1a-000000000004'),
    ('Coroa Partida',                '0b6e4c1a-0001-4a6e-9c1a-000000000005'),
    ('Guardiões da Muralha Gelada',  '0b6e4c1a-0001-4a6e-9c1a-000000000005'),
    ('Salão dos Duelos',             '0b6e4c1a-0001-4a6e-9c1a-000000000005'),
    ('Colmeia de Bronze',            '0b6e4c1a-0001-4a6e-9c1a-000000000006'),
    ('Lanternas do Rio',             '0b6e4c1a-0001-4a6e-9c1a-000000000006'),
    ('Relógio de Areia Negra',       '0b6e4c1a-0001-4a6e-9c1a-000000000006'),
    ('Torres de Cobalto',            '0b6e4c1a-0001-4a6e-9c1a-000000000006')
) AS m(game_name, category_id)
WHERE bg.name = m.game_name
  AND bg.category_id IS NULL;

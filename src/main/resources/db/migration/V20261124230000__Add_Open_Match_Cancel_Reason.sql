-- Motivo do cancelamento de uma partida do Jogue, informado pelo host e mostrado aos jogadores
-- (obrigatório na API, de 10 a 300 caracteres). Partidas antigas/abertas ficam com NULL.

ALTER TABLE open_match ADD COLUMN cancel_reason VARCHAR(300) NULL;

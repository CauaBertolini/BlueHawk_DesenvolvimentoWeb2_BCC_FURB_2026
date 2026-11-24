-- Detalhes do anúncio da Loja: estado de conservação da cópia e forma de entrega escolhida pelo vendedor.
-- Anúncios antigos ficam sem os dois (NULL); a API exige os dois só na criação.

ALTER TABLE offer ADD COLUMN condition VARCHAR(30) NULL;
ALTER TABLE offer ADD CONSTRAINT offer_condition_chk
    CHECK (condition IS NULL OR condition IN ('NOVO_LACRADO', 'COMO_NOVO', 'MUITO_BOM', 'BOM', 'COM_MARCAS_DE_USO'));

-- A_COMBINAR: à combinar; RETIRADA: o comprador cuida da coleta; ENVIO: o vendedor envia até o comprador
ALTER TABLE offer ADD COLUMN delivery_method VARCHAR(20) NULL;
ALTER TABLE offer ADD CONSTRAINT offer_delivery_method_chk
    CHECK (delivery_method IS NULL OR delivery_method IN ('A_COMBINAR', 'RETIRADA', 'ENVIO'));

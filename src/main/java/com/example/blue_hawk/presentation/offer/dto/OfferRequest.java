package com.example.blue_hawk.presentation.offer.dto;

import java.math.BigDecimal;

/**
 * Corpo de POST e PUT {@code /offers}. {@code condition}: NOVO_LACRADO | COMO_NOVO | MUITO_BOM | BOM |
 * COM_MARCAS_DE_USO; {@code deliveryMethod}: A_COMBINAR | RETIRADA | ENVIO (sem diferenciar maiúsculas).
 * Obrigatórios na criação; no PUT, null mantém os valores atuais.
 */
public record OfferRequest(
        String userBoardgameId,
        BigDecimal price,
        String status,
        String description,
        String condition,
        String deliveryMethod
) {}

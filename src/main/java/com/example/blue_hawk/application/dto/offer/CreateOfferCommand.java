package com.example.blue_hawk.application.dto.offer;

import com.example.blue_hawk.application.dto.auth.CurrentUser;

import java.math.BigDecimal;

/**
 * {@code condition}: estado de conservação e {@code deliveryMethod}: forma de entrega, ambos obrigatórios
 * (texto do JSON, convertido para enum sem diferenciar maiúsculas); {@code description}: obrigatória, de 10 a 500
 * caracteres.
 */
public record CreateOfferCommand(
        String userBoardgameId,
        BigDecimal price,
        String status,
        String description,
        String condition,
        String deliveryMethod,
        CurrentUser currentUser
) {}

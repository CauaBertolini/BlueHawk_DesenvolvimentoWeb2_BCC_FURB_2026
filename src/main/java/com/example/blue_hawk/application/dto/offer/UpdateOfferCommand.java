package com.example.blue_hawk.application.dto.offer;

import java.math.BigDecimal;

/** {@code condition} e {@code deliveryMethod} são opcionais: null mantém os valores atuais. */
public record UpdateOfferCommand(
        String id,
        String userBoardgameId,
        BigDecimal price,
        String status,
        String description,
        String condition,
        String deliveryMethod
) {}

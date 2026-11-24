package com.example.blue_hawk.presentation.offer.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OfferResponse(
        String id,
        String userBoardgameId,
        BigDecimal price,
        String status,
        String description,
        // Estado de conservação (null em anúncios antigos)
        String condition,
        // Forma de entrega (null em anúncios antigos)
        String deliveryMethod,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}

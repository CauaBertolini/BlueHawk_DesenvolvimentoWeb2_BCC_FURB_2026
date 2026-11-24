package com.example.blue_hawk.application.dto.offer;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PatchOfferOutput(
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

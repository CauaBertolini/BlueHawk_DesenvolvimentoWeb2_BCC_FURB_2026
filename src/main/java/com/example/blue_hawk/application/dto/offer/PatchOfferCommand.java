package com.example.blue_hawk.application.dto.offer;

import java.math.BigDecimal;

/** Campos null são mantidos. */
public record PatchOfferCommand(
        String id,
        BigDecimal price,
        String status,
        String description,
        String condition,
        String deliveryMethod
) {}

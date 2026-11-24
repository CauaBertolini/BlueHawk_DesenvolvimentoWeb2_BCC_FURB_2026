package com.example.blue_hawk.presentation.offer.dto;

import java.math.BigDecimal;

/** Corpo do PATCH {@code /offers/{id}}: campos null são mantidos. */
public record OfferPatchRequest(
        BigDecimal price,
        String status,
        String description,
        String condition,
        String deliveryMethod
) {}

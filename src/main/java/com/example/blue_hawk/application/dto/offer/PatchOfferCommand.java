package com.example.blue_hawk.application.dto.offer;

import com.example.blue_hawk.application.dto.auth.CurrentUser;

import java.math.BigDecimal;

/** Campos null são mantidos. */
public record PatchOfferCommand(
        String id,
        BigDecimal price,
        String status,
        String description,
        String condition,
        String deliveryMethod,
        CurrentUser currentUser
) {}

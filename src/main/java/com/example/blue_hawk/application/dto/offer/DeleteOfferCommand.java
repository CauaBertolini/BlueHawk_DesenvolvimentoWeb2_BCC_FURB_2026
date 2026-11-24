package com.example.blue_hawk.application.dto.offer;

import com.example.blue_hawk.application.dto.auth.CurrentUser;

public record DeleteOfferCommand(String id,
        CurrentUser currentUser
) {}

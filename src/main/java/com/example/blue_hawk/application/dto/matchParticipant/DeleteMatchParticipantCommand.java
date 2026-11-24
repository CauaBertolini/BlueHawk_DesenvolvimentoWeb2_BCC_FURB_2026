package com.example.blue_hawk.application.dto.matchParticipant;

import com.example.blue_hawk.application.dto.auth.CurrentUser;

public record DeleteMatchParticipantCommand(
        String id,
        CurrentUser currentUser
) {}

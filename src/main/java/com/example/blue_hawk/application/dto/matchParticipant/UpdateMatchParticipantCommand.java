package com.example.blue_hawk.application.dto.matchParticipant;

import com.example.blue_hawk.application.dto.auth.CurrentUser;

public record UpdateMatchParticipantCommand(
        String id,
        String matchId,
        String userId,
        CurrentUser currentUser
) {}

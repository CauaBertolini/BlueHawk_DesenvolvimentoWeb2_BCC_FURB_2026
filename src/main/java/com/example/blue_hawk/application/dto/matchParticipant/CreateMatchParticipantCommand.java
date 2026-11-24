package com.example.blue_hawk.application.dto.matchParticipant;

import com.example.blue_hawk.application.dto.auth.CurrentUser;

public record CreateMatchParticipantCommand(
        String matchId,
        String userId,
        CurrentUser currentUser
) {}

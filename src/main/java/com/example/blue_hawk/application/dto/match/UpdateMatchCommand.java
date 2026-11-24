package com.example.blue_hawk.application.dto.match;

import com.example.blue_hawk.application.dto.auth.CurrentUser;

public record UpdateMatchCommand(
        String id,
        String userBoardgameId,
        CurrentUser currentUser
) {
}

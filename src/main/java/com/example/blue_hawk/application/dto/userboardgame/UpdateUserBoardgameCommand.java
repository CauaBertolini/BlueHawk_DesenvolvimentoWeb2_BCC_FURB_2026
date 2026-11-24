package com.example.blue_hawk.application.dto.userboardgame;

import com.example.blue_hawk.application.dto.auth.CurrentUser;

public record UpdateUserBoardgameCommand(
        String id,
        String userId,
        String boardgameId,
        CurrentUser currentUser
) {
}

package com.example.blue_hawk.application.dto.userboardgame;

import com.example.blue_hawk.application.dto.auth.CurrentUser;

public record CreateUserBoardgameCommand(
        String userId,
        String boardgameId,
        CurrentUser currentUser
) {
}

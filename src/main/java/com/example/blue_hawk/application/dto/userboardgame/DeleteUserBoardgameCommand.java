package com.example.blue_hawk.application.dto.userboardgame;

import com.example.blue_hawk.application.dto.auth.CurrentUser;

public record DeleteUserBoardgameCommand(
        String id,
        CurrentUser currentUser
) {
}

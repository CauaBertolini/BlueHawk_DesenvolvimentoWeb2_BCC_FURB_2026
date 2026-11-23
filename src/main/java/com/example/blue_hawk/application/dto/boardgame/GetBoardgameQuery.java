package com.example.blue_hawk.application.dto.boardgame;

import com.example.blue_hawk.application.dto.auth.CurrentUser;

public record GetBoardgameQuery(
        String id,
        CurrentUser currentUser
) {
}

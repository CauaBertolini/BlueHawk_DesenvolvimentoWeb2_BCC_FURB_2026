package com.example.blue_hawk.application.dto.user;

import com.example.blue_hawk.application.dto.auth.CurrentUser;

public record DeleteUserCommand(
        String id,
        CurrentUser currentUser) {
}

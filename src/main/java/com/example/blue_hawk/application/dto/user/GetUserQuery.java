package com.example.blue_hawk.application.dto.user;

import com.example.blue_hawk.application.dto.auth.CurrentUser;

/** {@code currentUser}: quem consulta (define se e-mail e papel aparecem) */
public record GetUserQuery(
        String id,
        CurrentUser currentUser
) {
}

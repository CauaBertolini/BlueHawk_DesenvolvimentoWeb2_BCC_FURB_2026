package com.example.blue_hawk.application.dto.boardgame;

import com.example.blue_hawk.application.dto.auth.CurrentUser;

/**
 * {@code status}: APPROVED por padrão; PENDING/REJECTED só para ADMIN.
 * {@code sort}: "name" (padrão) ou "likes" (ranking de mais curtidos).
 */
public record ListBoardgameQuery(
        String name,
        String status,
        String sort,
        Integer page,
        Integer size,
        CurrentUser currentUser
) {
}

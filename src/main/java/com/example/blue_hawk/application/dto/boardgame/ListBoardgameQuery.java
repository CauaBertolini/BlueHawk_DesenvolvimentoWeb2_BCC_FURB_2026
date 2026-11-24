package com.example.blue_hawk.application.dto.boardgame;

import com.example.blue_hawk.application.dto.auth.CurrentUser;

/**
 * {@code status}: APPROVED por padrão; PENDING/REJECTED só para ADMIN.
 * {@code sort}: "name" (padrão) ou "likes" (ranking de mais curtidos).
 * {@code category}: nome da categoria (opcional).
 */
public record ListBoardgameQuery(
        String name,
        String status,
        String sort,
        Integer page,
        Integer size,
        CurrentUser currentUser,
        String category
) {
    /** Sem filtro de categoria */
    public ListBoardgameQuery(String name, String status, String sort, Integer page, Integer size,
                              CurrentUser currentUser) {
        this(name, status, sort, page, size, currentUser, null);
    }
}

package com.example.blue_hawk.application.dto.boardgame;

import com.example.blue_hawk.application.dto.auth.CurrentUser;

/**
 * Decisão do ADMIN sobre um jogo enviado. {@code note} é o motivo, usado na rejeição. {@code currentUser} é o
 * ADMIN que revisou (autor da notificação enviada a quem submeteu o jogo).
 */
public record ReviewBoardgameCommand(
        String id,
        boolean approve,
        String note,
        CurrentUser currentUser
) {
}

package com.example.blue_hawk.application.dto.boardgame;

/** Decisão do ADMIN sobre um jogo enviado. {@code note} é o motivo, usado na rejeição. */
public record ReviewBoardgameCommand(
        String id,
        boolean approve,
        String note
) {
}

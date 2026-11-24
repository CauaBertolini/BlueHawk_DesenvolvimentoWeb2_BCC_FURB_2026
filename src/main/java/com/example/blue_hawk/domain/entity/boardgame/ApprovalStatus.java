package com.example.blue_hawk.domain.entity.boardgame;

/**
 * Situação de um boardgame no catálogo (wiki).
 * Só APPROVED aparece para todos; PENDING e REJECTED ficam visíveis apenas para quem cadastrou e para ADMINs.
 * Persistido pelo nome (coluna {@code approval_status}).
 */
public enum ApprovalStatus {
    PENDING,
    APPROVED,
    REJECTED
}

package com.example.blue_hawk.presentation.boardgame;

/**
 * {@code approvalStatus}: PENDING | APPROVED | REJECTED.
 * {@code category}: nome da categoria (null em jogos antigos, sem categoria).
 * {@code coverPreset}: arte do site escolhida; {@code coverImageUrl}: caminho da foto enviada (relativo à API),
 * que tem prioridade sobre a arte. Os dois null = arte automática.
 * {@code userBoardgameId} só vem no POST de um usuário comum (a cópia criada na estante dele).
 */
public record BoardgameResponse(
        String id,
        String name,
        String description,
        Integer releaseYear,
        Integer playerNumber,
        Integer playTime,
        String approvalStatus,
        String submittedBy,
        String reviewNote,
        String categoryId,
        String category,
        String coverPreset,
        String coverImageUrl,
        /* Total de curtidas e se quem está vendo curtiu (false para visitantes) */
        Integer likeCount,
        Boolean likedByMe,
        String userBoardgameId
) {
    static String coverImageUrl(String id, Long version) {
        return version != null ? "/boardgames/" + id + "/cover?v=" + version : null;
    }
}

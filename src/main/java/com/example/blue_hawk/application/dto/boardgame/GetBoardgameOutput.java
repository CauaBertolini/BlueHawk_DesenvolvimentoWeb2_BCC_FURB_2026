package com.example.blue_hawk.application.dto.boardgame;

import com.example.blue_hawk.domain.entity.boardgame.Boardgame;

/**
 * {@code coverImageVersion}: momento (epoch ms) em que a foto de capa foi enviada; null = sem foto.
 * Serve para montar a URL da imagem sem cache antigo.
 */
public record GetBoardgameOutput(
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
        Long coverImageVersion,
        Integer likeCount,
        Boolean likedByMe
) {
    public static GetBoardgameOutput from(Boardgame b) {
        return new GetBoardgameOutput(
                b.getId().toString(),
                b.getName(),
                b.getDescription(),
                b.getReleaseYear(),
                b.getPlayerNumber(),
                b.getPlayTime(),
                b.getApprovalStatus().name(),
                b.getSubmittedBy() != null ? b.getSubmittedBy().toString() : null,
                b.getReviewNote(),
                b.getCategoryId() != null ? b.getCategoryId().toString() : null,
                b.getCategoryName(),
                b.getCoverPreset(),
                b.getCoverImageUpdatedAt() != null ? b.getCoverImageUpdatedAt().toEpochMilli() : null,
                b.getLikeCount(),
                b.isLikedByMe()
        );
    }
}

package com.example.blue_hawk.domain.entity.boardgame;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public class Boardgame {
    private UUID id;
    private String name;
    private String description;
    private int releaseYear;
    private Integer playerNumber;
    private int playTime;
    private ApprovalStatus approvalStatus = ApprovalStatus.APPROVED;
    /** Usuário que cadastrou o jogo (null para jogos sem autor registrado). */
    private UUID submittedBy;
    /** Motivo informado pelo ADMIN ao rejeitar. */
    private String reviewNote;
    private UUID categoryId;
    /** Só leitura: nome da categoria, preenchido ao carregar do banco. */
    private String categoryName;
    /** Arte do site escolhida como capa (ver {@link #COVER_PRESETS}); null = automática ou foto enviada. */
    private String coverPreset;
    /** Quando a foto de capa foi enviada; null = sem foto. */
    private Instant coverImageUpdatedAt;
    /** Só leitura: total de curtidas (coluna like_count, mantida junto com board_game_like). */
    private int likeCount;
    /** Só leitura: se quem está vendo curtiu (preenchido pelo caso de uso, não vem do banco). */
    private boolean likedByMe;

    /** Artes geométricas do site disponíveis como capa (mesmas chaves do componente GameCover do front). */
    public static final Set<String> COVER_PRESETS = Set.of(
            "waves-1", "waves-2", "beam-1", "beam-2", "peaks-1", "peaks-2", "valley", "board-1", "board-2");

    public Boardgame() {}

    public Boardgame(String name, String description, int releaseYear, Integer playerNumber, int playTime) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.description = description;
        this.releaseYear = releaseYear;
        this.playerNumber = playerNumber;
        this.playTime = playTime;
    }

    public Boardgame(UUID id, String name, String description, int releaseYear, Integer playerNumber, int playTime) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.releaseYear = releaseYear;
        this.playerNumber = playerNumber;
        this.playTime = playTime;
    }

    /** Jogo cadastrado por um usuário comum: fica pendente até um ADMIN revisar. */
    public static Boardgame submittedBy(UUID userId, String name, String description, int releaseYear,
                                        Integer playerNumber, int playTime) {
        Boardgame boardgame = new Boardgame(name, description, releaseYear, playerNumber, playTime);
        boardgame.approvalStatus = ApprovalStatus.PENDING;
        boardgame.submittedBy = userId;
        return boardgame;
    }

    public boolean isApproved() {
        return approvalStatus == ApprovalStatus.APPROVED;
    }

    public boolean isSubmittedBy(UUID userId) {
        return userId != null && userId.equals(submittedBy);
    }

    /** ADMIN edita qualquer jogo; o autor só o próprio enquanto não foi aprovado. */
    public boolean canBeEditedBy(UUID userId, boolean admin) {
        return admin || (isSubmittedBy(userId) && !isApproved());
    }

    /** Aprovados são públicos; pendentes/rejeitados só para o autor e para ADMINs. */
    public boolean isVisibleTo(UUID userId, boolean admin) {
        return isApproved() || admin || isSubmittedBy(userId);
    }

    public void approve() {
        this.approvalStatus = ApprovalStatus.APPROVED;
        this.reviewNote = null;
    }

    public void reject(String note) {
        this.approvalStatus = ApprovalStatus.REJECTED;
        this.reviewNote = note;
    }

    /** O autor corrigiu um jogo rejeitado: volta para a fila de aprovação. */
    public void resubmit() {
        if (approvalStatus == ApprovalStatus.REJECTED) {
            this.approvalStatus = ApprovalStatus.PENDING;
        }
    }

    public ApprovalStatus getApprovalStatus() {
        return approvalStatus;
    }

    public void setApprovalStatus(ApprovalStatus approvalStatus) {
        this.approvalStatus = approvalStatus;
    }

    public UUID getSubmittedBy() {
        return submittedBy;
    }

    public void setSubmittedBy(UUID submittedBy) {
        this.submittedBy = submittedBy;
    }

    public String getReviewNote() {
        return reviewNote;
    }

    public void setReviewNote(String reviewNote) {
        this.reviewNote = reviewNote;
    }

    public UUID getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(UUID categoryId) {
        this.categoryId = categoryId;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    public String getCoverPreset() {
        return coverPreset;
    }

    public void setCoverPreset(String coverPreset) {
        this.coverPreset = coverPreset;
    }

    public Instant getCoverImageUpdatedAt() {
        return coverImageUpdatedAt;
    }

    public void setCoverImageUpdatedAt(Instant coverImageUpdatedAt) {
        this.coverImageUpdatedAt = coverImageUpdatedAt;
    }

    public int getLikeCount() {
        return likeCount;
    }

    public void setLikeCount(int likeCount) {
        this.likeCount = likeCount;
    }

    public boolean isLikedByMe() {
        return likedByMe;
    }

    public void setLikedByMe(boolean likedByMe) {
        this.likedByMe = likedByMe;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getReleaseYear() {
        return releaseYear;
    }

    public void setReleaseYear(int releaseYear) {
        this.releaseYear = releaseYear;
    }

    public Integer getPlayerNumber() {
        return playerNumber;
    }

    public void setPlayerNumber(Integer playerNumber) {
        this.playerNumber = playerNumber;
    }

    public int getPlayTime() {
        return playTime;
    }

    public void setPlayTime(int playTime) {
        this.playTime = playTime;
    }
}

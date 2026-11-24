package com.example.blue_hawk.application.usecase.boardgame;

import com.example.blue_hawk.domain.entity.boardgame.Boardgame;
import com.example.blue_hawk.domain.entity.category.Category;
import com.example.blue_hawk.domain.repository.ICategoryRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/** Regras dos campos de um boardgame (limites vêm das colunas da tabela board_game). */
final class BoardgameValidation {

    private BoardgameValidation() {
    }

    static void validate(String name, String description, Integer releaseYear, Integer playerNumber, Integer playTime) {
        if (name == null || name.isBlank() || name.trim().length() > 150) {
            throw badRequest("name is required (max 150 characters)");
        }
        if (description == null || description.isBlank() || description.trim().length() > 150) {
            throw badRequest("description is required (max 150 characters)");
        }
        if (releaseYear == null || releaseYear < 1 || releaseYear > 9999) {
            throw badRequest("releaseYear is required");
        }
        if (playerNumber == null || playerNumber < 1) {
            throw badRequest("playerNumber must be at least 1");
        }
        if (playTime == null || playTime < 1) {
            throw badRequest("playTime must be at least 1");
        }
    }

    /** Categoria é obrigatória e precisa existir na tabela category */
    static UUID requireCategory(ICategoryRepository categoryRepository, String categoryId) {
        if (categoryId == null || categoryId.isBlank()) {
            throw badRequest("categoryId is required");
        }
        UUID id;
        try {
            id = UUID.fromString(categoryId.trim());
        } catch (IllegalArgumentException e) {
            throw badRequest("categoryId is invalid");
        }
        return categoryRepository.findById(id)
                .map(Category::id)
                .orElseThrow(() -> badRequest("categoryId does not exist"));
    }

    /** Arte do site escolhida como capa: vazio = automática; senão, uma das {@link Boardgame#COVER_PRESETS} */
    static String normalizeCoverPreset(String coverPreset) {
        if (coverPreset == null || coverPreset.isBlank()) {
            return null;
        }
        String preset = coverPreset.trim();
        if (!Boardgame.COVER_PRESETS.contains(preset)) {
            throw badRequest("coverPreset is invalid");
        }
        return preset;
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}

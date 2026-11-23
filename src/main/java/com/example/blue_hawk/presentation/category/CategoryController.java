package com.example.blue_hawk.presentation.category;

import com.example.blue_hawk.domain.repository.ICategoryRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Categorias de boardgame (públicas: usadas no cadastro de jogo e nos filtros). */
@RestController
@RequestMapping("/categories")
public class CategoryController {

    public record CategoryResponse(String id, String name) {
    }

    private final ICategoryRepository categoryRepository;

    public CategoryController(ICategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @GetMapping
    public List<CategoryResponse> list() {
        return categoryRepository.findAll().stream()
                .map(c -> new CategoryResponse(c.id().toString(), c.name()))
                .toList();
    }
}

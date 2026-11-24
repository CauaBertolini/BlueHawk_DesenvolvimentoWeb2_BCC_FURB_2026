package com.example.blue_hawk.domain.repository;

import com.example.blue_hawk.domain.entity.category.Category;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ICategoryRepository {
    /** Todas as categorias, em ordem alfabética */
    List<Category> findAll();

    Optional<Category> findById(UUID id);
}

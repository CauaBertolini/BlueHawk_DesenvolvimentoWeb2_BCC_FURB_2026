package com.example.blue_hawk.infrastructure.persistence.category;

import com.example.blue_hawk.domain.entity.category.Category;
import com.example.blue_hawk.domain.repository.ICategoryRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class CategoryRepositoryImpl implements ICategoryRepository {

    private final CategoryJpaRepository categoryJpaRepository;

    public CategoryRepositoryImpl(CategoryJpaRepository categoryJpaRepository) {
        this.categoryJpaRepository = categoryJpaRepository;
    }

    @Override
    public List<Category> findAll() {
        return categoryJpaRepository.findAllByOrderByNameAsc().stream()
                .map(e -> new Category(e.getId(), e.getName()))
                .toList();
    }

    @Override
    public Optional<Category> findById(UUID id) {
        return categoryJpaRepository.findById(id).map(e -> new Category(e.getId(), e.getName()));
    }
}

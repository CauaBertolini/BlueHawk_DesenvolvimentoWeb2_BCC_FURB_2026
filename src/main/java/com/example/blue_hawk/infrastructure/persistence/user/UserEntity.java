package com.example.blue_hawk.infrastructure.persistence.user;

import com.example.blue_hawk.domain.entity.user.User;
import com.example.blue_hawk.domain.entity.user.UserRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

// Aspas duplas preservam a palavra reservada "user" no PostgreSQL.
@Entity
@Table(name = "\"user\"")
public class UserEntity {

    // A migration define id como CHAR(36); sem isso o Hibernate usaria BINARY(16).
    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "id", nullable = false, updatable = false, length = 36)
    private UUID id;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "email", nullable = false, length = 150)
    private String email;

    // Hash BCrypt, nunca a senha em texto puro.
    @Column(name = "password", nullable = false, length = 150)
    private String password;

    @Enumerated(EnumType.ORDINAL)
    @Column(name = "role", nullable = false)
    private UserRole role;

    // Só leitura: o banco preenche no INSERT (DEFAULT CURRENT_TIMESTAMP) e o valor nunca muda.
    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    protected UserEntity() {
    }

    public static UserEntity fromDomain(User user) {
        UserEntity entity = new UserEntity();
        entity.id = user.getId();
        entity.name = user.getName();
        entity.email = user.getEmail();
        entity.password = user.getPasswordHash();
        entity.role = user.getRole();
        entity.createdAt = user.getCreatedAt() != null
                ? LocalDateTime.ofInstant(user.getCreatedAt(), ZoneOffset.UTC)
                : null;
        return entity;
    }

    public User toDomain() {
        User user = new User(id, name, email, password, role);
        user.setCreatedAt(createdAt != null ? createdAt.toInstant(ZoneOffset.UTC) : null);
        return user;
    }
}

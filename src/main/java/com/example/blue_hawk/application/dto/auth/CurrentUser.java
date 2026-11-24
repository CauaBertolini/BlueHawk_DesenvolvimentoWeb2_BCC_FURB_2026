package com.example.blue_hawk.application.dto.auth;

import java.util.UUID;

/**
 * O usuário da requisição atual, já resolvido a partir do token: quem está logado e se é ADMIN.
 * Para visitantes (sem token), use {@link #anonymous()}: {@code userId} é null e {@code admin} é false.
 *
 * <p>Os casos de uso recebem este objeto em vez de ler o Spring Security, para as regras de permissão
 * (autor, host, ADMIN) ficarem na camada de aplicação e testáveis sem o framework.</p>
 */
public record CurrentUser(UUID userId, boolean admin) {

    public static CurrentUser anonymous() {
        return new CurrentUser(null, false);
    }

    public boolean authenticated() {
        return userId != null;
    }

    /** É o próprio usuário {@code id} (visitante nunca é) */
    public boolean isSelf(UUID id) {
        return userId != null && userId.equals(id);
    }

    /** É o próprio usuário {@code id} ou um ADMIN */
    public boolean isSelfOrAdmin(UUID id) {
        return admin || isSelf(id);
    }
}

package com.example.blue_hawk.application.usecase.user;

import com.example.blue_hawk.application.dto.auth.CurrentUser;
import com.example.blue_hawk.application.dto.user.UserProfileDtos.PublicProfileView;

/** Perfil público de um usuário (aberto a partir dos anúncios, chats e da caixa de conversas). */
public interface PublicProfileUseCase {

    /**
     * Exige login (401). Respeita as preferências do dono: perfil privado mostra só id, nome e avatar; estante
     * oculta deixa {@code shelf} null. O dono sempre vê tudo. 404 para usuário inexistente.
     */
    PublicProfileView get(String userId, CurrentUser currentUser);
}

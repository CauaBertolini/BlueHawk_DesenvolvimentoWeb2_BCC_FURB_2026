package com.example.blue_hawk.application.usecase.user;

import com.example.blue_hawk.application.dto.auth.CurrentUser;
import com.example.blue_hawk.application.dto.user.UserProfileDtos.MyProfileView;
import com.example.blue_hawk.application.dto.user.UserProfileDtos.UpdateMyProfileCommand;
import com.example.blue_hawk.domain.repository.IUserProfileRepository.AvatarImage;

/** Perfil do próprio usuário (extras, preferências e avatar). Tudo exige login, menos ler a foto. */
public interface UserProfileUseCase {

    /** Sem linha no banco: os padrões (público, estante visível, sem extras, sem avatar) */
    MyProfileView getMine(CurrentUser currentUser);

    MyProfileView updateMine(UpdateMyProfileCommand command, CurrentUser currentUser);

    /** Envia (ou troca) a foto: JPEG, PNG ou WebP até 3 MB. Apaga a arte escolhida. */
    MyProfileView uploadAvatar(byte[] data, CurrentUser currentUser);

    /** Remove a foto E a arte: o front volta às iniciais */
    MyProfileView removeAvatar(CurrentUser currentUser);

    /** Lê a foto de qualquer usuário (pública: {@code <img>} não envia token). 404 sem foto. */
    AvatarImage getAvatar(String userId);
}

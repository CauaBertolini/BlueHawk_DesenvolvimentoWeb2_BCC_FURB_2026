package com.example.blue_hawk.presentation.user;

import com.example.blue_hawk.application.dto.auth.CurrentUser;
import com.example.blue_hawk.application.dto.user.UserProfileDtos.MyProfileView;
import com.example.blue_hawk.application.dto.user.UserProfileDtos.PublicProfileView;
import com.example.blue_hawk.application.dto.user.UserProfileDtos.UpdateMyProfileCommand;
import com.example.blue_hawk.application.usecase.user.PublicProfileUseCase;
import com.example.blue_hawk.application.usecase.user.UserProfileUseCase;
import com.example.blue_hawk.domain.repository.IUserProfileRepository.AvatarImage;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.Duration;

/**
 * Perfil do usuário: o próprio (extras, preferências e avatar, em {@code /users/me/...}) e o perfil público dos
 * outros. A foto ({@code GET /users/{id}/avatar}) é pública porque {@code <img>} não envia token.
 */
@RestController
@RequestMapping("/users")
public class UserProfileController {

    private final UserProfileUseCase userProfileUseCase;
    private final PublicProfileUseCase publicProfileUseCase;

    public UserProfileController(UserProfileUseCase userProfileUseCase, PublicProfileUseCase publicProfileUseCase) {
        this.userProfileUseCase = userProfileUseCase;
        this.publicProfileUseCase = publicProfileUseCase;
    }

    @GetMapping("/me/profile")
    public MyProfileView getMine(CurrentUser currentUser) {
        return userProfileUseCase.getMine(currentUser);
    }

    @PutMapping("/me/profile")
    public MyProfileView updateMine(@RequestBody UpdateMyProfileCommand command, CurrentUser currentUser) {
        return userProfileUseCase.updateMine(command, currentUser);
    }

    /** Envia a foto (multipart, campo "file"; JPEG, PNG ou WebP até 3 MB). Apaga a arte escolhida. */
    @PostMapping(value = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public MyProfileView uploadAvatar(@RequestPart("file") MultipartFile file, CurrentUser currentUser) {
        byte[] data;
        try {
            data = file.getBytes();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not read the file");
        }
        return userProfileUseCase.uploadAvatar(data, currentUser);
    }

    /** Remove a foto e a arte (o front volta às iniciais) */
    @DeleteMapping("/me/avatar")
    public MyProfileView removeAvatar(CurrentUser currentUser) {
        return userProfileUseCase.removeAvatar(currentUser);
    }

    /** A URL leva a versão (?v=), então a imagem pode ficar em cache por muito tempo */
    @GetMapping("/{id}/avatar")
    public ResponseEntity<byte[]> getAvatar(@PathVariable String id) {
        AvatarImage avatar = userProfileUseCase.getAvatar(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(avatar.contentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic())
                .header("X-Content-Type-Options", "nosniff")
                .body(avatar.data());
    }

    @GetMapping("/{id}/public-profile")
    public PublicProfileView publicProfile(@PathVariable String id, CurrentUser currentUser) {
        return publicProfileUseCase.get(id, currentUser);
    }
}

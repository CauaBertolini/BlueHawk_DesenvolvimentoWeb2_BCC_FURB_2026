package com.example.blue_hawk.application.usecase.user;

import com.example.blue_hawk.application.dto.auth.CurrentUser;
import com.example.blue_hawk.application.dto.user.UserProfileDtos.MyProfileView;
import com.example.blue_hawk.application.dto.user.UserProfileDtos.UpdateMyProfileCommand;
import com.example.blue_hawk.domain.entity.user.UserProfile;
import com.example.blue_hawk.domain.repository.IUserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserProfileUseCaseImplTest {

    private static final UUID ME = UUID.randomUUID();
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0};

    @Mock
    private IUserProfileRepository profileRepository;

    private UserProfileUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        useCase = new UserProfileUseCaseImpl(profileRepository);
        when(profileRepository.findByUserId(any())).thenReturn(Optional.empty());
        when(profileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private static CurrentUser me() {
        return new CurrentUser(ME, false);
    }

    private static UpdateMyProfileCommand command(String bio, String level, String preset) {
        return new UpdateMyProfileCommand(bio, null, level, null, null, null, null, null, null, preset);
    }

    private UserProfile withImage() {
        UserProfile profile = new UserProfile(ME, "Oi", null, null, null, true, true, true, false, true,
                null, "image/png", Instant.ofEpochMilli(1_700_000_000_000L), Instant.now(), ME, null, null);
        when(profileRepository.findByUserId(ME)).thenReturn(Optional.of(profile));
        return profile;
    }

    @Test
    void defaultsWhenThereIsNoRow() {
        MyProfileView view = useCase.getMine(me());

        assertNull(view.bio());
        assertNull(view.experienceLevel());
        assertTrue(view.isPublic());
        assertTrue(view.showShelf());
        assertTrue(view.matchAlerts());
        assertFalse(view.offerAlerts());
        assertNull(view.avatarPreset());
        assertNull(view.avatarUrl());
    }

    @Test
    void requiresLogin() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> useCase.getMine(CurrentUser.anonymous()));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
    }

    @Test
    void updateTrimsTextsKeepsBooleansWhenNullAndRecordsAudit() {
        MyProfileView view = useCase.updateMine(new UpdateMyProfileCommand("  Gosto de euro  ", "   ",
                "veterano", "Estratégia", false, null, null, true, null, "meeple-blue"), me());

        assertEquals("Gosto de euro", view.bio());
        assertNull(view.city(), "vazio vira null");
        assertEquals("VETERANO", view.experienceLevel());
        assertFalse(view.isPublic());
        assertTrue(view.showShelf(), "null mantém o atual");
        assertTrue(view.matchAlerts());
        assertTrue(view.offerAlerts());
        assertEquals("meeple-blue", view.avatarPreset());

        ArgumentCaptor<UserProfile> saved = ArgumentCaptor.forClass(UserProfile.class);
        verify(profileRepository).save(saved.capture());
        assertEquals(ME, saved.getValue().getCreatedBy());
        assertEquals(ME, saved.getValue().getUpdatedBy());
    }

    @Test
    void rejectsInvalidLevelLengthsAndPreset() {
        assertBadRequest(command(null, "MESTRE", null));
        assertBadRequest(command("x".repeat(501), null, null));
        assertBadRequest(new UpdateMyProfileCommand(null, "c".repeat(101), null, null, null, null, null, null, null,
                null));
        assertBadRequest(new UpdateMyProfileCommand(null, null, null, "g".repeat(201), null, null, null, null, null,
                null));
        assertBadRequest(command(null, null, "Meeple Azul"));
        assertBadRequest(command(null, null, "a".repeat(41)));
        assertBadRequest(command(null, null, "../x"));
        verify(profileRepository, never()).save(any());

        // Nos limites passa
        useCase.updateMine(command("x".repeat(500), "INICIANTE", "a".repeat(40)), me());
    }

    private void assertBadRequest(UpdateMyProfileCommand command) {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> useCase.updateMine(command, me()));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void choosingPresetClearsUploadedImage() {
        withImage();

        MyProfileView view = useCase.updateMine(command("Oi", null, "dice-red"), me());

        assertEquals("dice-red", view.avatarPreset());
        assertNull(view.avatarUrl());
        verify(profileRepository).saveAvatarImage(eq(ME), isNull());
    }

    @Test
    void updatingWithoutPresetKeepsUploadedImage() {
        withImage();

        MyProfileView view = useCase.updateMine(command("Novo", null, null), me());

        assertEquals("/users/" + ME + "/avatar?v=1700000000000", view.avatarUrl());
        verify(profileRepository, never()).saveAvatarImage(any(), any());
    }

    @Test
    void uploadingImageClearsPreset() {
        UserProfile profile = UserProfile.defaults(ME);
        profile.choosePreset("dice-red");
        when(profileRepository.findByUserId(ME)).thenReturn(Optional.of(profile));

        MyProfileView view = useCase.uploadAvatar(PNG, me());

        assertNull(view.avatarPreset());
        assertTrue(view.avatarUrl().startsWith("/users/" + ME + "/avatar?v="));
        verify(profileRepository).saveAvatarImage(ME, PNG);
    }

    @Test
    void uploadValidatesFile() {
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> useCase.uploadAvatar(new byte[0], me())).getStatusCode());
        byte[] big = new byte[UserProfileUseCaseImpl.MAX_AVATAR_BYTES + 1];
        System.arraycopy(PNG, 0, big, 0, PNG.length);
        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, assertThrows(ResponseStatusException.class,
                () -> useCase.uploadAvatar(big, me())).getStatusCode());
        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, assertThrows(ResponseStatusException.class,
                () -> useCase.uploadAvatar("<svg/>".getBytes(), me())).getStatusCode());
        verify(profileRepository, never()).saveAvatarImage(any(), any());
    }

    @Test
    void removeClearsImageAndPreset() {
        withImage();

        MyProfileView view = useCase.removeAvatar(me());

        assertNull(view.avatarUrl());
        assertNull(view.avatarPreset());
        verify(profileRepository).saveAvatarImage(eq(ME), isNull());
    }

    @Test
    void avatarIsNotFoundWithoutImage() {
        when(profileRepository.findAvatarImage(ME)).thenReturn(Optional.empty());

        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                () -> useCase.getAvatar(ME.toString())).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                () -> useCase.getAvatar("nope")).getStatusCode());
    }

    @Test
    void directMessagesPreferenceRoundTrip() {
        assertTrue(useCase.getMine(me()).allowDirectMessages(), "padrão: aceita");

        MyProfileView off = useCase.updateMine(new UpdateMyProfileCommand(null, null, null, null, null, null, null,
                null, false, null), me());
        assertFalse(off.allowDirectMessages());

        ArgumentCaptor<UserProfile> saved = ArgumentCaptor.forClass(UserProfile.class);
        verify(profileRepository).save(saved.capture());
        when(profileRepository.findByUserId(ME)).thenReturn(Optional.of(saved.getValue()));

        MyProfileView kept = useCase.updateMine(command("Oi", null, null), me());
        assertFalse(kept.allowDirectMessages(), "null mantém o atual");
        assertFalse(useCase.getMine(me()).allowDirectMessages());
    }
}

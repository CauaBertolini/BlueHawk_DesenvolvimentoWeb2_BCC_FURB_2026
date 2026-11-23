package com.example.blue_hawk.application.usecase.boardgame;

import com.example.blue_hawk.application.dto.auth.CurrentUser;
import com.example.blue_hawk.application.dto.boardgame.GetBoardgameOutput;
import com.example.blue_hawk.domain.entity.boardgame.Boardgame;
import com.example.blue_hawk.domain.repository.IBoardgameCoverRepository;
import com.example.blue_hawk.domain.repository.IBoardgameRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BoardgameCoverUseCaseTest {

    private static final UUID AUTHOR = UUID.randomUUID();
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0};

    @Mock
    private IBoardgameRepository boardgameRepository;
    @Mock
    private IBoardgameCoverRepository coverRepository;

    private BoardgameCoverUseCaseImpl useCase() {
        return new BoardgameCoverUseCaseImpl(boardgameRepository, coverRepository);
    }

    @Test
    void detectsImageTypeFromBytes() {
        assertEquals("image/png", BoardgameCoverUseCaseImpl.detectImageType(PNG));
        assertEquals("image/jpeg", BoardgameCoverUseCaseImpl.detectImageType(JPEG));
        assertEquals("image/webp", BoardgameCoverUseCaseImpl.detectImageType(
                new byte[]{'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'}));
        assertNull(BoardgameCoverUseCaseImpl.detectImageType("<svg onload=alert(1)>".getBytes()));
    }

    @Test
    void authorUploadsCoverOnPendingGame() {
        Boardgame pending = Boardgame.submittedBy(AUTHOR, "X", "Y", 2024, 2, 30);
        pending.setCoverPreset("waves-1");
        when(boardgameRepository.findById(pending.getId())).thenReturn(Optional.of(pending));
        when(boardgameRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        GetBoardgameOutput output = useCase().upload(pending.getId().toString(), PNG, new CurrentUser(AUTHOR, false));

        verify(coverRepository).save(eq(pending.getId()), eq("image/png"), eq(PNG), any());
        assertNotNull(output.coverImageVersion());
        assertNull(output.coverPreset(), "a foto substitui a arte escolhida");
    }

    @Test
    void rejectsNonImageFiles() {
        Boardgame pending = Boardgame.submittedBy(AUTHOR, "X", "Y", 2024, 2, 30);
        when(boardgameRepository.findById(pending.getId())).thenReturn(Optional.of(pending));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                useCase().upload(pending.getId().toString(), "not an image".getBytes(), new CurrentUser(AUTHOR, false)));
        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ex.getStatusCode());
        verify(coverRepository, never()).save(any(), anyString(), any(), any());
    }

    @Test
    void userCannotChangeCoverOfApprovedGame() {
        Boardgame approved = new Boardgame("Catan", "Classico", 1995, 4, 90);
        when(boardgameRepository.findById(approved.getId())).thenReturn(Optional.of(approved));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                useCase().upload(approved.getId().toString(), PNG, new CurrentUser(AUTHOR, false)));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void removingCoverClearsVersion() {
        Boardgame pending = Boardgame.submittedBy(AUTHOR, "X", "Y", 2024, 2, 30);
        pending.setCoverImageUpdatedAt(java.time.Instant.now());
        when(boardgameRepository.findById(pending.getId())).thenReturn(Optional.of(pending));
        when(boardgameRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        GetBoardgameOutput output = useCase().remove(pending.getId().toString(), new CurrentUser(AUTHOR, false));

        verify(coverRepository).deleteByBoardgameId(pending.getId());
        assertNull(output.coverImageVersion());
    }
}

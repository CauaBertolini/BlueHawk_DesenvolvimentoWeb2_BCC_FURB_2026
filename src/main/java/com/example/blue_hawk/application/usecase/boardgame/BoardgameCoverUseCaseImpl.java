package com.example.blue_hawk.application.usecase.boardgame;

import com.example.blue_hawk.application.dto.auth.CurrentUser;
import com.example.blue_hawk.application.dto.boardgame.GetBoardgameOutput;
import com.example.blue_hawk.domain.entity.boardgame.Boardgame;
import com.example.blue_hawk.domain.repository.IBoardgameCoverRepository;
import com.example.blue_hawk.domain.repository.IBoardgameCoverRepository.CoverImage;
import com.example.blue_hawk.domain.repository.IBoardgameRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class BoardgameCoverUseCaseImpl implements BoardgameCoverUseCase {

    /** Limite da foto já redimensionada pelo front; o Spring também barra multipart acima disso */
    static final int MAX_BYTES = 2 * 1024 * 1024;

    private final IBoardgameRepository boardgameRepository;
    private final IBoardgameCoverRepository coverRepository;

    public BoardgameCoverUseCaseImpl(IBoardgameRepository boardgameRepository,
                                     IBoardgameCoverRepository coverRepository) {
        this.boardgameRepository = boardgameRepository;
        this.coverRepository = coverRepository;
    }

    @Override
    @Transactional
    public GetBoardgameOutput upload(String boardgameId, byte[] data, CurrentUser currentUser) {
        Boardgame boardgame = editable(boardgameId, currentUser);
        if (data == null || data.length == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "file is required");
        }
        if (data.length > MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "file must be at most 2 MB");
        }
        // O tipo vem dos bytes, não do Content-Type informado pelo cliente
        String contentType = detectImageType(data);
        if (contentType == null) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "file must be a JPEG, PNG or WebP image");
        }

        // Precisão de milissegundos: é o que vai na URL (coverImageVersion)
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        coverRepository.save(boardgame.getId(), contentType, data, now);
        boardgame.setCoverImageUpdatedAt(now);
        boardgame.setCoverPreset(null);
        if (!currentUser.admin()) {
            boardgame.resubmit();
        }
        return GetBoardgameOutput.from(boardgameRepository.save(boardgame));
    }

    @Override
    @Transactional
    public GetBoardgameOutput remove(String boardgameId, CurrentUser currentUser) {
        Boardgame boardgame = editable(boardgameId, currentUser);
        coverRepository.deleteByBoardgameId(boardgame.getId());
        boardgame.setCoverImageUpdatedAt(null);
        if (!currentUser.admin()) {
            boardgame.resubmit();
        }
        return GetBoardgameOutput.from(boardgameRepository.save(boardgame));
    }

    @Override
    public CoverImage get(String boardgameId) {
        return coverRepository.findByBoardgameId(parseId(boardgameId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cover not found"));
    }

    private Boardgame editable(String boardgameId, CurrentUser currentUser) {
        CurrentUser user = currentUser != null ? currentUser : CurrentUser.anonymous();
        Boardgame boardgame = boardgameRepository.findById(parseId(boardgameId))
                .filter(bg -> bg.isVisibleTo(user.userId(), user.admin()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Boardgame not found"));
        if (!boardgame.canBeEditedBy(user.userId(), user.admin())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only admins can edit catalog games");
        }
        return boardgame;
    }

    private static UUID parseId(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Boardgame not found");
        }
    }

    /**
     * JPEG (FF D8 FF), PNG (89 50 4E 47) ou WebP (RIFF....WEBP); null para qualquer outra coisa.
     * Também usado pelo upload do avatar do usuário.
     */
    public static String detectImageType(byte[] d) {
        if (d.length >= 3 && (d[0] & 0xFF) == 0xFF && (d[1] & 0xFF) == 0xD8 && (d[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (d.length >= 8 && (d[0] & 0xFF) == 0x89 && d[1] == 'P' && d[2] == 'N' && d[3] == 'G') {
            return "image/png";
        }
        if (d.length >= 12 && d[0] == 'R' && d[1] == 'I' && d[2] == 'F' && d[3] == 'F'
                && d[8] == 'W' && d[9] == 'E' && d[10] == 'B' && d[11] == 'P') {
            return "image/webp";
        }
        return null;
    }
}

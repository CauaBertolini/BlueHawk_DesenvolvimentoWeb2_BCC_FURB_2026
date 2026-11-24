package com.example.blue_hawk.presentation.boardgame;

import com.example.blue_hawk.application.dto.auth.CurrentUser;
import com.example.blue_hawk.application.dto.boardgame.CreateBoardgameCommand;
import com.example.blue_hawk.application.dto.boardgame.CreateBoardgameOutput;
import com.example.blue_hawk.application.dto.boardgame.DeleteBoardgameCommand;
import com.example.blue_hawk.application.dto.boardgame.GetBoardgameOutput;
import com.example.blue_hawk.application.dto.boardgame.GetBoardgameQuery;
import com.example.blue_hawk.application.dto.boardgame.ListBoardgameOutput;
import com.example.blue_hawk.application.dto.boardgame.ListBoardgameQuery;
import com.example.blue_hawk.application.dto.boardgame.ReviewBoardgameCommand;
import com.example.blue_hawk.application.dto.boardgame.UpdateBoardgameCommand;
import com.example.blue_hawk.application.dto.boardgame.UpdateBoardgameOutput;
import com.example.blue_hawk.application.usecase.boardgame.BoardgameCoverUseCase;
import com.example.blue_hawk.application.usecase.boardgame.CreateBoardgameUseCase;
import com.example.blue_hawk.application.usecase.boardgame.DeleteBoardgameUseCase;
import com.example.blue_hawk.application.usecase.boardgame.GetBoardgameUseCase;
import com.example.blue_hawk.application.usecase.boardgame.LikeBoardgameUseCase;
import com.example.blue_hawk.application.usecase.boardgame.ListBoardgameUseCase;
import com.example.blue_hawk.application.usecase.boardgame.ReviewBoardgameUseCase;
import com.example.blue_hawk.application.usecase.boardgame.UpdateBoardgameUseCase;
import com.example.blue_hawk.domain.repository.IBoardgameCoverRepository.CoverImage;
import com.example.blue_hawk.presentation.auth.Roles;
import com.example.blue_hawk.presentation.dto.ApiResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

/**
 * Catálogo de boardgames (wiki). Usuário comum cadastra jogos que possui (ficam PENDING e vão para a estante dele);
 * só ADMIN aprova/rejeita, exclui e vê a fila de aprovação.
 */
@RestController
@RequestMapping("/boardgames")
public class BoardgameController {

        private final CreateBoardgameUseCase createBoardgameUseCase;
    private final GetBoardgameUseCase getBoardgameUseCase;
    private final ListBoardgameUseCase listBoardgameUseCase;
    private final UpdateBoardgameUseCase updateBoardgameUseCase;
    private final DeleteBoardgameUseCase deleteBoardgameUseCase;
    private final ReviewBoardgameUseCase reviewBoardgameUseCase;
    private final BoardgameCoverUseCase boardgameCoverUseCase;
    private final LikeBoardgameUseCase likeBoardgameUseCase;

    public BoardgameController(CreateBoardgameUseCase createBoardgameUseCase,
                               GetBoardgameUseCase getBoardgameUseCase,
                               ListBoardgameUseCase listBoardgameUseCase,
                               UpdateBoardgameUseCase updateBoardgameUseCase,
                               DeleteBoardgameUseCase deleteBoardgameUseCase,
                               ReviewBoardgameUseCase reviewBoardgameUseCase,
                               BoardgameCoverUseCase boardgameCoverUseCase,
                               LikeBoardgameUseCase likeBoardgameUseCase) {
        this.likeBoardgameUseCase = likeBoardgameUseCase;
        this.createBoardgameUseCase = createBoardgameUseCase;
        this.getBoardgameUseCase = getBoardgameUseCase;
        this.listBoardgameUseCase = listBoardgameUseCase;
        this.updateBoardgameUseCase = updateBoardgameUseCase;
        this.deleteBoardgameUseCase = deleteBoardgameUseCase;
        this.reviewBoardgameUseCase = reviewBoardgameUseCase;
        this.boardgameCoverUseCase = boardgameCoverUseCase;
    }

    @PostMapping
    public ResponseEntity<BoardgameResponse> create(@RequestBody BoardgameRequest request, CurrentUser currentUser) {
        CreateBoardgameCommand command = new CreateBoardgameCommand(
                request.name(),
                request.description(),
                request.releaseYear(),
                request.playerNumber(),
                request.playTime(),
                request.categoryId(),
                request.coverPreset(),
                currentUser
        );
        CreateBoardgameOutput o = createBoardgameUseCase.handle(command);
        return ResponseEntity.status(HttpStatus.CREATED).body(new BoardgameResponse(
                o.id(), o.name(), o.description(), o.releaseYear(), o.playerNumber(), o.playTime(),
                o.approvalStatus(), o.submittedBy(), o.reviewNote(), o.categoryId(), o.category(),
                o.coverPreset(), BoardgameResponse.coverImageUrl(o.id(), o.coverImageVersion()),
                o.likeCount(), o.likedByMe(),o.userBoardgameId()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<BoardgameResponse> getById(@PathVariable String id, CurrentUser currentUser) {
        return ResponseEntity.ok(toResponse(getBoardgameUseCase.handle(new GetBoardgameQuery(id, currentUser))));
    }

    /** Sem {@code status}: só aprovados (a wiki). {@code status=PENDING|REJECTED}: fila de aprovação, só ADMIN. */
    @GetMapping
    public ResponseEntity<List<BoardgameResponse>> list(
            @RequestParam(name = "name", required = false) String name,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "sort", required = false) String sort,
            @RequestParam(name = "page", defaultValue = "0") Integer page,
            @RequestParam(name = "size", defaultValue = "10") Integer size,
            @RequestParam(name = "category", required = false) String category,
            CurrentUser currentUser) {

        ListBoardgameQuery query = new ListBoardgameQuery(name, status, sort, page, size, currentUser, category);
        List<BoardgameResponse> response = listBoardgameUseCase.handle(query).stream()
                .map(BoardgameController::toResponse)
                .toList();
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<BoardgameResponse> update(
            @PathVariable String id,
            @RequestBody BoardgameRequest request,
            CurrentUser currentUser) {

        UpdateBoardgameCommand command = new UpdateBoardgameCommand(
                id,
                request.name(),
                request.description(),
                request.releaseYear(),
                request.playerNumber(),
                request.playTime(),
                request.categoryId(),
                request.coverPreset(),
                currentUser
        );
        UpdateBoardgameOutput o = updateBoardgameUseCase.handle(command);
        return ResponseEntity.ok(new BoardgameResponse(
                o.id(), o.name(), o.description(), o.releaseYear(), o.playerNumber(), o.playTime(),
                o.approvalStatus(), o.submittedBy(), o.reviewNote(), o.categoryId(), o.category(),
                o.coverPreset(), BoardgameResponse.coverImageUrl(o.id(), o.coverImageVersion()),
                o.likeCount(), o.likedByMe(),null));
    }

    /** Envia a foto de capa (multipart, campo "file"; JPEG, PNG ou WebP até 2 MB) */
    @PostMapping(value = "/{id}/cover", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<BoardgameResponse> uploadCover(@PathVariable String id,
                                                         @RequestPart("file") MultipartFile file,
                                                         CurrentUser currentUser) {
        byte[] data;
        try {
            data = file.getBytes();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not read the file");
        }
        return ResponseEntity.ok(toResponse(boardgameCoverUseCase.upload(id, data, currentUser)));
    }

    /** Remove a foto de capa; a capa volta a ser a arte escolhida (ou a automática) */
    @DeleteMapping("/{id}/cover")
    public ResponseEntity<BoardgameResponse> removeCover(@PathVariable String id, CurrentUser currentUser) {
        return ResponseEntity.ok(toResponse(boardgameCoverUseCase.remove(id, currentUser)));
    }

    /** A URL leva a versão (?v=), então a imagem pode ficar em cache por muito tempo */
    @GetMapping("/{id}/cover")
    public ResponseEntity<byte[]> getCover(@PathVariable String id) {
        CoverImage cover = boardgameCoverUseCase.get(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(cover.contentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic())
                .header("X-Content-Type-Options", "nosniff")
                .body(cover.data());
    }

    /** Curtir (idempotente). Devolve o total atualizado. */
    @PostMapping("/{id}/like")
    public LikeBoardgameUseCase.LikeOutput like(@PathVariable String id, CurrentUser currentUser) {
        return likeBoardgameUseCase.like(id, currentUser);
    }

    @DeleteMapping("/{id}/like")
    public LikeBoardgameUseCase.LikeOutput unlike(@PathVariable String id, CurrentUser currentUser) {
        return likeBoardgameUseCase.unlike(id, currentUser);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize(Roles.ADMIN_ONLY)
    public ResponseEntity<BoardgameResponse> approve(@PathVariable String id, CurrentUser currentUser) {
        return ResponseEntity.ok(toResponse(
                reviewBoardgameUseCase.handle(new ReviewBoardgameCommand(id, true, null, currentUser))));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize(Roles.ADMIN_ONLY)
    public ResponseEntity<BoardgameResponse> reject(@PathVariable String id,
                                                    @RequestBody(required = false) BoardgameReviewRequest request,
                                                    CurrentUser currentUser) {
        String reason = request != null ? request.reason() : null;
        return ResponseEntity.ok(toResponse(
                reviewBoardgameUseCase.handle(new ReviewBoardgameCommand(id, false, reason, currentUser))));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(Roles.ADMIN_ONLY)
    public ApiResponse<Void> delete(@PathVariable String id) {
        deleteBoardgameUseCase.handle(new DeleteBoardgameCommand(id));
        return ApiResponse.empty();
    }

    private static BoardgameResponse toResponse(GetBoardgameOutput o) {
        return new BoardgameResponse(o.id(), o.name(), o.description(), o.releaseYear(), o.playerNumber(),
                o.playTime(), o.approvalStatus(), o.submittedBy(), o.reviewNote(), o.categoryId(), o.category(),
                o.coverPreset(), BoardgameResponse.coverImageUrl(o.id(), o.coverImageVersion()),
                o.likeCount(), o.likedByMe(),null);
    }

    private static BoardgameResponse toResponse(ListBoardgameOutput o) {
        return new BoardgameResponse(o.id(), o.name(), o.description(), o.releaseYear(), o.playerNumber(),
                o.playTime(), o.approvalStatus(), o.submittedBy(), o.reviewNote(), o.categoryId(), o.category(),
                o.coverPreset(), BoardgameResponse.coverImageUrl(o.id(), o.coverImageVersion()),
                o.likeCount(), o.likedByMe(),null);
    }
}

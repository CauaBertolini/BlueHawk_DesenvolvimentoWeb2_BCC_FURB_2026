package com.example.blue_hawk.presentation.direct;

import com.example.blue_hawk.application.dto.auth.CurrentUser;
import com.example.blue_hawk.application.dto.direct.DirectConversationDtos.ConversationView;
import com.example.blue_hawk.application.dto.direct.DirectConversationDtos.MessageView;
import com.example.blue_hawk.application.usecase.direct.DirectConversationUseCase;
import com.example.blue_hawk.application.usecase.direct.DirectConversationUseCase.OpenResult;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Mensagens diretas entre usuários do site. Todas as rotas exigem login. */
@RestController
public class DirectConversationController {

    public record OpenConversationRequest(String userId) {
    }

    public record SendMessageRequest(String body) {
    }

    private final DirectConversationUseCase directConversationUseCase;

    public DirectConversationController(DirectConversationUseCase directConversationUseCase) {
        this.directConversationUseCase = directConversationUseCase;
    }

    /** Get-or-create da conversa com {@code userId}: 200 se já existia, 201 se foi criada agora */
    @PostMapping("/direct-conversations")
    public ResponseEntity<ConversationView> open(@RequestBody(required = false) OpenConversationRequest request,
                                                 CurrentUser currentUser) {
        OpenResult result = directConversationUseCase.open(request != null ? request.userId() : null, currentUser);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(result.conversation());
    }

    @GetMapping("/direct-conversations/{id}")
    public ConversationView get(@PathVariable String id, CurrentUser currentUser) {
        return directConversationUseCase.get(id, currentUser);
    }

    /** {@code after}: só as mensagens posteriores (ISO-8601) */
    @GetMapping("/direct-conversations/{id}/messages")
    public List<MessageView> messages(@PathVariable String id,
                                      @RequestParam(name = "after", required = false) String after,
                                      CurrentUser currentUser) {
        return directConversationUseCase.messages(id, after, currentUser);
    }

    @PostMapping("/direct-conversations/{id}/messages")
    public ResponseEntity<MessageView> send(@PathVariable String id,
                                            @RequestBody(required = false) SendMessageRequest request,
                                            CurrentUser currentUser) {
        MessageView message = directConversationUseCase.send(id, request != null ? request.body() : null,
                currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(message);
    }
}

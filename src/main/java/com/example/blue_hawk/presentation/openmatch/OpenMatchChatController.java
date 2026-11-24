package com.example.blue_hawk.presentation.openmatch;

import com.example.blue_hawk.application.dto.auth.CurrentUser;
import com.example.blue_hawk.application.dto.openmatch.OpenMatchChatDtos.ChatsView;
import com.example.blue_hawk.application.dto.openmatch.OpenMatchChatDtos.MessageView;
import com.example.blue_hawk.application.usecase.openmatch.OpenMatchChatUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Chat das partidas do Jogue (mesa e conversas diretas: host ↔ pedido e jogador ↔ jogador aceitos). Todas as
 * rotas exigem login.
 */
@RestController
public class OpenMatchChatController {

    /** {@code recipientId} null = mensagem para a mesa */
    public record SendMessageRequest(String body, String recipientId) {
    }

    private final OpenMatchChatUseCase chatUseCase;

    public OpenMatchChatController(OpenMatchChatUseCase chatUseCase) {
        this.chatUseCase = chatUseCase;
    }

    @GetMapping("/open-matches/{id}/chats")
    public ChatsView chats(@PathVariable String id, CurrentUser currentUser) {
        return chatUseCase.chats(id, currentUser);
    }

    /** Sem {@code with} = mesa. {@code after}: só as mensagens posteriores (ISO-8601). */
    @GetMapping("/open-matches/{id}/messages")
    public List<MessageView> messages(@PathVariable String id,
                                      @RequestParam(name = "with", required = false) String with,
                                      @RequestParam(name = "after", required = false) String after,
                                      CurrentUser currentUser) {
        return chatUseCase.messages(id, with, after, currentUser);
    }

    @PostMapping("/open-matches/{id}/messages")
    public ResponseEntity<MessageView> send(@PathVariable String id,
                                            @RequestBody(required = false) SendMessageRequest request,
                                            CurrentUser currentUser) {
        MessageView message = chatUseCase.send(id, request != null ? request.body() : null,
                request != null ? request.recipientId() : null, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(message);
    }
}

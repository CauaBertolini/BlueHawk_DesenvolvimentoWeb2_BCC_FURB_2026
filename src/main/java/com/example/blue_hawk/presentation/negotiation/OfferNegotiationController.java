package com.example.blue_hawk.presentation.negotiation;

import com.example.blue_hawk.application.dto.auth.CurrentUser;
import com.example.blue_hawk.application.dto.negotiation.OfferNegotiationDtos.MessageView;
import com.example.blue_hawk.application.dto.negotiation.OfferNegotiationDtos.NegotiationView;
import com.example.blue_hawk.application.usecase.negotiation.OfferNegotiationUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/** Negociação dos anúncios da Loja (comprador ↔ vendedor). Todas as rotas exigem login. */
@RestController
public class OfferNegotiationController {

    /** {@code proposedPrice} null = mensagem sem proposta */
    public record SendMessageRequest(String body, BigDecimal proposedPrice) {
    }

    public record AcceptRequest(String messageId) {
    }

    private final OfferNegotiationUseCase negotiationUseCase;

    public OfferNegotiationController(OfferNegotiationUseCase negotiationUseCase) {
        this.negotiationUseCase = negotiationUseCase;
    }

    /** Começa (ou continua) a negociação de quem está logado no anúncio, com a primeira mensagem */
    @PostMapping("/offers/{offerId}/negotiations")
    public ResponseEntity<NegotiationView> start(@PathVariable String offerId,
                                                 @RequestBody(required = false) SendMessageRequest request,
                                                 CurrentUser currentUser) {
        NegotiationView negotiation = negotiationUseCase.start(offerId, request != null ? request.body() : null,
                request != null ? request.proposedPrice() : null, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(negotiation);
    }

    @GetMapping("/negotiations/{id}")
    public NegotiationView get(@PathVariable String id, CurrentUser currentUser) {
        return negotiationUseCase.get(id, currentUser);
    }

    /** {@code after}: só as mensagens posteriores (ISO-8601) */
    @GetMapping("/negotiations/{id}/messages")
    public List<MessageView> messages(@PathVariable String id,
                                      @RequestParam(name = "after", required = false) String after,
                                      CurrentUser currentUser) {
        return negotiationUseCase.messages(id, after, currentUser);
    }

    @PostMapping("/negotiations/{id}/messages")
    public ResponseEntity<MessageView> send(@PathVariable String id,
                                            @RequestBody(required = false) SendMessageRequest request,
                                            CurrentUser currentUser) {
        MessageView message = negotiationUseCase.send(id, request != null ? request.body() : null,
                request != null ? request.proposedPrice() : null, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(message);
    }

    @PostMapping("/negotiations/{id}/accept")
    public NegotiationView accept(@PathVariable String id,
                                  @RequestBody(required = false) AcceptRequest request,
                                  CurrentUser currentUser) {
        return negotiationUseCase.accept(id, request != null ? request.messageId() : null, currentUser);
    }
}

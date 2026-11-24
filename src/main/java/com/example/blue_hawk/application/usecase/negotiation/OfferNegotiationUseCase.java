package com.example.blue_hawk.application.usecase.negotiation;

import com.example.blue_hawk.application.dto.auth.CurrentUser;
import com.example.blue_hawk.application.dto.negotiation.OfferNegotiationDtos.MessageView;
import com.example.blue_hawk.application.dto.negotiation.OfferNegotiationDtos.NegotiationView;

import java.math.BigDecimal;
import java.util.List;

/**
 * Negociação dos anúncios da Loja entre comprador e vendedor (dono da cópia anunciada).
 * <ul>
 *   <li>Ninguém negocia o próprio anúncio (403). Começar exige anúncio ACTIVE (409); uma negociação por
 *   comprador por anúncio, reaproveitada ao começar de novo (409 se já ACCEPTED/CLOSED).</li>
 *   <li>Só comprador e vendedor leem e escrevem (403). Escrever: OPEN ou ACCEPTED (409 se CLOSED); depois do
 *   aceite (ACCEPTED) servem só para combinar a entrega, sem nova proposta de preço (409).</li>
 *   <li>Aceitar: só o vendedor, uma proposta enviada pelo comprador, negociação OPEN e anúncio ACTIVE (409).
 *   O anúncio vira SOLD e as outras negociações abertas dele são encerradas (CLOSED).</li>
 * </ul>
 */
public interface OfferNegotiationUseCase {

    /** Até quantas mensagens (as mais recentes) uma leitura devolve */
    int MESSAGES_LIMIT = 200;
    int BODY_MAX = 1000;
    BigDecimal PRICE_MAX = new BigDecimal("99999999.99");

    /** Cria ou reaproveita a negociação do comprador no anúncio e envia a mensagem */
    NegotiationView start(String offerId, String body, BigDecimal proposedPrice, CurrentUser currentUser);

    NegotiationView get(String negotiationId, CurrentUser currentUser);

    /**
     * Mensagens em ordem cronológica. {@code after} (ISO-8601, opcional): só as posteriores. Marca como lidas as
     * notificações de mensagem dessa negociação de quem está lendo.
     */
    List<MessageView> messages(String negotiationId, String after, CurrentUser currentUser);

    MessageView send(String negotiationId, String body, BigDecimal proposedPrice, CurrentUser currentUser);

    /** O vendedor aceita a proposta da mensagem {@code messageId} */
    NegotiationView accept(String negotiationId, String messageId, CurrentUser currentUser);
}

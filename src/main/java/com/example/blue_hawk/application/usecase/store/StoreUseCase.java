package com.example.blue_hawk.application.usecase.store;

import com.example.blue_hawk.application.dto.auth.CurrentUser;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/** Vitrine da Loja: anúncios ativos de jogos do catálogo, com jogo e vendedor já resolvidos. */
public interface StoreUseCase {

    enum Sort { RECENT, PRICE }

    record StoreGame(String id, String name, String description, String category, String coverPreset,
                     Long coverImageVersion, Integer playerNumber, Integer playTime, Integer releaseYear) {
    }

    /**
     * {@code condition}: estado de conservação e {@code deliveryMethod}: forma de entrega, pelo nome do enum
     * (null em anúncios antigos).
     */
    record StoreOffer(String id, BigDecimal price, String description, String condition,
                      String deliveryMethod, String createdAt, String sellerId,
                      String sellerName, boolean isMine, StoreGame game) {
    }

    /**
     * {@code query}: trecho do nome do jogo; {@code category}: nome exato da categoria. Os dois opcionais.
     */
    List<StoreOffer> list(Sort sort, String query, String category, int limit, CurrentUser currentUser);

    /**
     * Um anúncio da vitrine (página do produto). Vazio se não existe, não está mais ativo
     * ou o jogo não está no catálogo: para quem chega pelo link, é "indisponível".
     */
    Optional<StoreOffer> get(String offerId, CurrentUser currentUser);
}

package com.example.blue_hawk.application.usecase.store;

import com.example.blue_hawk.application.dto.auth.CurrentUser;

import java.math.BigDecimal;
import java.util.List;

/** Vitrine da Loja: anúncios ativos de jogos do catálogo, com jogo e vendedor já resolvidos. */
public interface StoreUseCase {

    enum Sort { RECENT, PRICE }

    record StoreGame(String id, String name, String description, String category, String coverPreset,
                     Long coverImageVersion, Integer playerNumber, Integer playTime, Integer releaseYear) {
    }

    record StoreOffer(String id, BigDecimal price, String description, String createdAt, String sellerName,
                      boolean isMine, StoreGame game) {
    }

    /**
     * {@code query}: trecho do nome do jogo; {@code category}: nome exato da categoria. Os dois opcionais.
     */
    List<StoreOffer> list(Sort sort, String query, String category, int limit, CurrentUser currentUser);
}

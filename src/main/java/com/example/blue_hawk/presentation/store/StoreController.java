package com.example.blue_hawk.presentation.store;

import com.example.blue_hawk.application.dto.auth.CurrentUser;
import com.example.blue_hawk.application.usecase.store.StoreUseCase.StoreOffer;
import com.example.blue_hawk.application.usecase.store.StoreUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

/** Vitrine pública da Loja (anúncios ativos de jogos do catálogo). */
@RestController
@RequestMapping("/store")
public class StoreController {

    public record StoreGameResponse(String id, String name, String description, String category, String coverPreset,
                                    String coverImageUrl, Integer playerNumber, Integer playTime,
                                    Integer releaseYear) {
    }

    public record StoreOfferResponse(String id, BigDecimal price, String description, String condition,
                                     String deliveryMethod, String createdAt,
                                     String sellerId, String sellerName, boolean isMine,
                                     StoreGameResponse game) {
    }

    private final StoreUseCase storeUseCase;

    public StoreController(StoreUseCase storeUseCase) {
        this.storeUseCase = storeUseCase;
    }

    /** {@code sort}: recent (padrão) | price. {@code q}: nome do jogo. {@code category}: nome da categoria. */
    @GetMapping("/offers")
    public List<StoreOfferResponse> offers(@RequestParam(name = "sort", defaultValue = "recent") String sort,
                                           @RequestParam(name = "q", required = false) String query,
                                           @RequestParam(name = "category", required = false) String category,
                                           @RequestParam(name = "limit", defaultValue = "24") int limit,
                                           CurrentUser currentUser) {
        StoreUseCase.Sort order = switch (sort.toLowerCase(Locale.ROOT)) {
            case "recent" -> StoreUseCase.Sort.RECENT;
            case "price" -> StoreUseCase.Sort.PRICE;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sort must be recent or price");
        };
        return storeUseCase.list(order, query, category, limit, currentUser).stream()
                .map(StoreController::toResponse)
                .toList();
    }

    /** Página do anúncio. 404 se não existe ou não está mais à venda. */
    @GetMapping("/offers/{id}")
    public StoreOfferResponse offer(@PathVariable String id, CurrentUser currentUser) {
        return storeUseCase.get(id, currentUser)
                .map(StoreController::toResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Offer not available"));
    }

    private static StoreOfferResponse toResponse(StoreOffer o) {
        var g = o.game();
        String coverUrl = g.coverImageVersion() != null
                ? "/boardgames/" + g.id() + "/cover?v=" + g.coverImageVersion()
                : null;
        return new StoreOfferResponse(o.id(), o.price(), o.description(), o.condition(), o.deliveryMethod(),
                o.createdAt(), o.sellerId(), o.sellerName(),
                o.isMine(),
                new StoreGameResponse(g.id(), g.name(), g.description(), g.category(), g.coverPreset(), coverUrl,
                        g.playerNumber(), g.playTime(), g.releaseYear()));
    }
}

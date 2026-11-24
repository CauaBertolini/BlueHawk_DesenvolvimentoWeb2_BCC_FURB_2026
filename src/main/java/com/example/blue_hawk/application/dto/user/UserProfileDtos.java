package com.example.blue_hawk.application.dto.user;

import java.math.BigDecimal;
import java.util.List;

/** DTOs do perfil do usuário (o próprio e o público). Datas em ISO-8601 UTC. */
public final class UserProfileDtos {

    private UserProfileDtos() {
    }

    /**
     * Perfil do próprio usuário: extras, preferências e avatar. {@code allowDirectMessages}: aceita mensagens
     * diretas de outros usuários.
     * {@code avatarUrl}: {@code /users/{id}/avatar?v={epochMilli}} quando há foto enviada, senão null.
     */
    public record MyProfileView(String bio, String city, String experienceLevel, String favoriteGenres,
                                boolean isPublic, boolean showShelf, boolean matchAlerts, boolean offerAlerts,
                                boolean allowDirectMessages, String avatarPreset, String avatarUrl) {
    }

    /**
     * Corpo do {@code PUT /users/me/profile}. Textos: null ou vazio limpa. Booleanos: null mantém o valor atual.
     * {@code avatarPreset}: escolher uma arte apaga a foto enviada; null só limpa a arte.
     */
    public record UpdateMyProfileCommand(String bio, String city, String experienceLevel, String favoriteGenres,
                                         Boolean isPublic, Boolean showShelf, Boolean matchAlerts,
                                         Boolean offerAlerts, Boolean allowDirectMessages, String avatarPreset) {
    }

    /** Extras visíveis no perfil público. {@code memberSince}: criação da conta. */
    public record ProfileInfo(String bio, String city, String experienceLevel, String favoriteGenres,
                              String memberSince) {
    }

    /**
     * {@code games}: cópias na estante; {@code forSale}: anúncios ativos; {@code matchesPlayed}: partidas do
     * Jogue já realizadas (host ou aceito, não canceladas, data passada).
     */
    public record ProfileStats(int games, int forSale, long matchesPlayed) {
    }

    /** Uma cópia da estante, com o anúncio ativo dela (se houver) */
    public record ShelfItem(String boardgameId, String name, String category, String coverPreset,
                            String coverImageUrl, String offerId, BigDecimal price) {
    }

    /**
     * Perfil público. Perfil privado visto por outra pessoa: só id, nome, papel e avatar ({@code profile},
     * {@code stats} e {@code shelf} null). Público: {@code profile} e {@code stats}; {@code shelf} só se o dono
     * deixou a estante visível. O dono sempre vê tudo. {@code acceptsDirectMessages}: a pessoa aceita mensagens
     * diretas (sempre presente, mesmo no perfil privado; true sem linha de perfil).
     */
    public record PublicProfileView(String id, String name, String role, String avatarPreset, String avatarUrl,
                                    boolean isPublic, boolean isMe, ProfileInfo profile, ProfileStats stats,
                                    List<ShelfItem> shelf, boolean acceptsDirectMessages) {
    }
}

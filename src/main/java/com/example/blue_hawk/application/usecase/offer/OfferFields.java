package com.example.blue_hawk.application.usecase.offer;

import com.example.blue_hawk.domain.entity.offer.DeliveryMethod;
import com.example.blue_hawk.domain.entity.offer.OfferCondition;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.Locale;

/**
 * Validação e conversão dos detalhes do anúncio (estado de conservação, forma de entrega e descrição),
 * compartilhadas pelos casos de uso de criar e editar anúncio. O JSON traz texto; aqui ele vira enum,
 * sem diferenciar maiúsculas. Valor ausente (onde é obrigatório) ou desconhecido → 400.
 */
public final class OfferFields {

    public static final int DESCRIPTION_MIN = 10;
    public static final int DESCRIPTION_MAX = 500;

    private OfferFields() {
    }

    /** Um dos valores de {@link OfferCondition}. Obrigatório. */
    public static OfferCondition parseCondition(String raw) {
        return parse(OfferCondition.class, raw, "condition");
    }

    /** Um dos valores de {@link DeliveryMethod}. Obrigatório. */
    public static DeliveryMethod parseDeliveryMethod(String raw) {
        return parse(DeliveryMethod.class, raw, "deliveryMethod");
    }

    /** Descrição obrigatória na criação: depois do trim, de 10 a 500 caracteres. Devolve o texto aparado. */
    public static String requireDescription(String raw) {
        String description = raw == null ? "" : raw.trim();
        if (description.length() < DESCRIPTION_MIN || description.length() > DESCRIPTION_MAX) {
            throw badRequest("description must have between " + DESCRIPTION_MIN + " and " + DESCRIPTION_MAX
                    + " characters");
        }
        return description;
    }

    /** Nome do enum para a resposta (null em anúncios antigos) */
    public static String nameOf(Enum<?> value) {
        return value != null ? value.name() : null;
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String raw, String field) {
        if (raw == null || raw.isBlank()) {
            throw badRequest(field + " is required");
        }
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw badRequest(field + " must be one of " + Arrays.toString(type.getEnumConstants()));
        }
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}

package com.example.blue_hawk.domain.entity.offer;

/** Forma de entrega escolhida pelo vendedor no anúncio. */
public enum DeliveryMethod {
    /** À combinar entre comprador e vendedor */
    A_COMBINAR,
    /** O comprador cuida da coleta */
    RETIRADA,
    /** O vendedor envia até o comprador */
    ENVIO
}

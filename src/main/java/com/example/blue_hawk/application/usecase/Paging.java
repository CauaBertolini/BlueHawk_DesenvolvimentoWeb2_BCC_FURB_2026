package com.example.blue_hawk.application.usecase;

/** Limites das listagens paginadas: sem teto, {@code ?size=1000000} carregaria a tabela inteira. */
public final class Paging {

    public static final int DEFAULT_SIZE = 10;
    public static final int MAX_SIZE = 100;

    private Paging() {
    }

    /** Página nunca negativa (null vira 0). */
    public static int page(Integer page) {
        return page == null ? 0 : Math.max(page, 0);
    }

    /** Tamanho entre 1 e {@link #MAX_SIZE} (null vira {@link #DEFAULT_SIZE}). */
    public static int size(Integer size) {
        return size(size, DEFAULT_SIZE);
    }

    /** Idem, com outro tamanho padrão */
    public static int size(Integer size, int defaultSize) {
        return size == null ? defaultSize : Math.clamp(size, 1, MAX_SIZE);
    }
}

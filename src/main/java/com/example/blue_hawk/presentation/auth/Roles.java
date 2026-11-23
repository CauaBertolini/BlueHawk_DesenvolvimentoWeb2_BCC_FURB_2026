package com.example.blue_hawk.presentation.auth;

/** Authorities do Spring Security (o papel do usuário vira "SCOPE_" + papel). */
public final class Roles {

    public static final String ADMIN_AUTHORITY = "SCOPE_ADMIN";

    /** Para {@code @PreAuthorize}: só ADMIN */
    public static final String ADMIN_ONLY = "hasAuthority('" + ADMIN_AUTHORITY + "')";

    private Roles() {
    }
}

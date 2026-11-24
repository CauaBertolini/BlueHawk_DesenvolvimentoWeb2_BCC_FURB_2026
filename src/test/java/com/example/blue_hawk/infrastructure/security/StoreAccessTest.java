package com.example.blue_hawk.infrastructure.security;

import com.example.blue_hawk.application.usecase.store.StoreUseCase;
import com.example.blue_hawk.application.usecase.store.StoreUseCase.StoreGame;
import com.example.blue_hawk.application.usecase.store.StoreUseCase.StoreOffer;
import com.example.blue_hawk.domain.repository.IUserRepository;
import com.example.blue_hawk.presentation.store.StoreController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Loja: a vitrine (listagem) é pública; abrir um anúncio exige login. */
@WebMvcTest(
        controllers = StoreController.class,
        properties = {
                "security.jwt.secret=test-secret-with-at-least-32-characters!!",
                "security.jwt.expiration-seconds=3600",
                "app.cors.allowed-origins=http://localhost:5173"
        })
@Import({SecurityConfig.class, JwtTokenService.class, TokenBlacklistService.class, ClientIpResolver.class})
class StoreAccessTest {

    private static final String OFFER_ID = UUID.randomUUID().toString();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StoreUseCase storeUseCase;
    @MockitoBean
    private IUserRepository userRepository;

    @BeforeEach
    void setUp() {
        StoreOffer offer = new StoreOffer(OFFER_ID, new BigDecimal("150.00"), "Cópia completa", "BOM", "A_COMBINAR",
                "2026-11-24T10:00:00", UUID.randomUUID().toString(), "Alice", false,
                new StoreGame(UUID.randomUUID().toString(), "Catan", "Desc", "Estratégia", null, null, 4, 90, 1995));
        when(storeUseCase.list(any(), any(), any(), anyInt(), any())).thenReturn(List.of(offer));
        when(storeUseCase.get(eq(OFFER_ID), any())).thenReturn(Optional.of(offer));
    }

    @Test
    void visitorSeesTheShowcase() throws Exception {
        mockMvc.perform(get("/store/offers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(OFFER_ID));
    }

    @Test
    void visitorCannotOpenAnOffer() throws Exception {
        mockMvc.perform(get("/store/offers/" + OFFER_ID)).andExpect(status().isUnauthorized());

        verify(storeUseCase, never()).get(any(), any());
    }

    @Test
    void loggedUserOpensAnOffer() throws Exception {
        mockMvc.perform(get("/store/offers/" + OFFER_ID)
                        .with(jwt().jwt(token -> token.subject(UUID.randomUUID().toString()))
                                .authorities(new SimpleGrantedAuthority("SCOPE_USER"))))
                .andExpect(status().isOk());

        verify(storeUseCase).get(eq(OFFER_ID), any());
    }
}

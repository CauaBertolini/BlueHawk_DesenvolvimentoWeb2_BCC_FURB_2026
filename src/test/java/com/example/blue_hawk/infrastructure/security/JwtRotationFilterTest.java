package com.example.blue_hawk.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JwtRotationFilterTest {

    @Mock
    private JwtTokenService jwtTokenService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    private JwtRotationFilter filter;
    private AutoCloseable closeable;

    @BeforeEach
    void setUp() {
        closeable = MockitoAnnotations.openMocks(this);
        filter = new JwtRotationFilter(jwtTokenService);
    }

    @AfterEach
    void tearDown() throws Exception {
        SecurityContextHolder.clearContext();
        closeable.close();
    }

    @Test
    void shouldRotateTokenWhenRequestIsAuthenticated() throws ServletException, IOException {
        Jwt jwt = new Jwt(
                "mock-token-value",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Map.of("alg", "HS256"),
                Map.of("sub", "user-123", "scope", "ADMIN")
        );

        // O token antigo dizia ADMIN, mas o papel atual (vindo do banco, nas authorities) é USER:
        // o token renovado deve sair com o papel atual
        JwtAuthenticationToken authentication = new JwtAuthenticationToken(
                jwt, List.of(new SimpleGrantedAuthority("SCOPE_USER")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        when(jwtTokenService.shouldRotate(jwt)).thenReturn(true);
        when(jwtTokenService.rotateToken(jwt, "USER")).thenReturn("new-rotated-token");

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setHeader(HttpHeaders.AUTHORIZATION, "Bearer new-rotated-token");
        verify(response).setHeader("accessToken", "new-rotated-token");
        verify(response).setHeader("X-New-Token", "new-rotated-token");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldKeepAFreshTokenInsteadOfRotatingOnEveryRequest() throws ServletException, IOException {
        Jwt jwt = new Jwt("fresh-token", Instant.now(), Instant.now().plusSeconds(3600),
                Map.of("alg", "HS256"), Map.of("sub", "user-123"));
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("SCOPE_USER"))));
        when(jwtTokenService.shouldRotate(jwt)).thenReturn(false);

        filter.doFilterInternal(request, response, filterChain);

        verify(jwtTokenService, never()).rotateToken(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(response, never()).setHeader(eq("X-New-Token"), org.mockito.ArgumentMatchers.anyString());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldNotRotateTokenWhenRequestIsNotAuthenticated() throws ServletException, IOException {
        SecurityContextHolder.clearContext();

        filter.doFilterInternal(request, response, filterChain);

        verify(response, never()).setHeader(eq(HttpHeaders.AUTHORIZATION), org.mockito.ArgumentMatchers.anyString());
        verify(response, never()).setHeader(eq("accessToken"), org.mockito.ArgumentMatchers.anyString());
        verify(response, never()).setHeader(eq("X-New-Token"), org.mockito.ArgumentMatchers.anyString());
        verify(filterChain).doFilter(request, response);
    }
}

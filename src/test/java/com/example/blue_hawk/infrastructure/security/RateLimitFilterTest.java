package com.example.blue_hawk.infrastructure.security;

import com.example.blue_hawk.infrastructure.security.RateLimitFilter.Limits;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateLimitFilterTest {

    private static final String ORIGIN = "http://localhost:5173";

    private final AtomicLong now = new AtomicLong(1_000_000_000L);

    private RateLimitFilter filter(Limits limits, boolean trustForwardedFor) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(List.of(ORIGIN));
        cors.setAllowedMethods(List.of("GET", "POST"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return new RateLimitFilter(true, limits, 1024, new ClientIpResolver(trustForwardedFor), source, now::get);
    }

    private static MockHttpServletRequest request(String method, String path, String ip) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRemoteAddr(ip);
        return request;
    }

    private static MockHttpServletResponse run(RateLimitFilter filter, MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    private void advance(Duration time) {
        now.addAndGet(time.toNanos());
    }

    @Test
    void loginIsBlockedAfterTheLimitAndReleasedAsTheWindowPasses() throws Exception {
        RateLimitFilter filter = filter(new Limits(1000, 1000, 3, 1000), false);

        for (int i = 0; i < 3; i++) {
            assertEquals(200, run(filter, request("POST", "/users/authenticate", "1.1.1.1")).getStatus());
        }
        MockHttpServletResponse blocked = run(filter, request("POST", "/users/authenticate/", "1.1.1.1"));
        assertEquals(429, blocked.getStatus());
        assertNotNull(blocked.getHeader("Retry-After"));
        assertTrue(blocked.getContentAsString().contains("\"status\":429"));

        // Outro IP e outras rotas seguem livres
        assertEquals(200, run(filter, request("POST", "/users/authenticate", "2.2.2.2")).getStatus());
        assertEquals(200, run(filter, request("GET", "/boardgames", "1.1.1.1")).getStatus());

        advance(Duration.ofSeconds(21));
        assertEquals(200, run(filter, request("POST", "/users/authenticate", "1.1.1.1")).getStatus());
        assertEquals(429, run(filter, request("POST", "/users/authenticate", "1.1.1.1")).getStatus());
    }

    @Test
    void signupWritesAndGeneralTrafficHaveTheirOwnLimits() throws Exception {
        RateLimitFilter filter = filter(new Limits(6, 2, 1000, 1), false);

        assertEquals(200, run(filter, request("POST", "/users", "1.1.1.1")).getStatus());
        assertEquals(429, run(filter, request("POST", "/users", "1.1.1.1")).getStatus());
        advance(Duration.ofMinutes(59));
        assertEquals(429, run(filter, request("POST", "/users", "1.1.1.1")).getStatus());

        assertEquals(200, run(filter, request("POST", "/offers", "3.3.3.3")).getStatus());
        assertEquals(200, run(filter, request("DELETE", "/offers/1", "3.3.3.3")).getStatus());
        assertEquals(429, run(filter, request("PATCH", "/offers/1", "3.3.3.3")).getStatus());

        // Limite geral: 6 por minuto somando tudo (as 3 escritas acima contam)
        for (int i = 0; i < 3; i++) {
            assertEquals(200, run(filter, request("GET", "/boardgames", "3.3.3.3")).getStatus());
        }
        assertEquals(429, run(filter, request("GET", "/boardgames", "3.3.3.3")).getStatus());
    }

    @Test
    void preflightIsNeverCounted() throws Exception {
        RateLimitFilter filter = filter(new Limits(1, 1, 1, 1), false);
        for (int i = 0; i < 5; i++) {
            MockHttpServletRequest preflight = request("OPTIONS", "/users/authenticate", "1.1.1.1");
            preflight.addHeader("Origin", ORIGIN);
            preflight.addHeader("Access-Control-Request-Method", "POST");
            assertEquals(200, run(filter, preflight).getStatus());
        }
    }

    @Test
    void rejectionCarriesCorsHeadersSoTheBrowserShowsTheStatus() throws Exception {
        RateLimitFilter filter = filter(new Limits(1, 1000, 1000, 1000), false);
        run(filter, request("GET", "/boardgames", "1.1.1.1"));

        MockHttpServletRequest second = request("GET", "/boardgames", "1.1.1.1");
        second.addHeader("Origin", ORIGIN);
        MockHttpServletResponse blocked = run(filter, second);

        assertEquals(429, blocked.getStatus());
        assertEquals(ORIGIN, blocked.getHeader("Access-Control-Allow-Origin"));
    }

    @Test
    void oversizedJsonBodyIsRefusedButMultipartIsLeftToItsOwnLimit() throws Exception {
        RateLimitFilter filter = filter(new Limits(1000, 1000, 1000, 1000), false);

        MockHttpServletRequest json = request("POST", "/offers", "1.1.1.1");
        json.setContentType("application/json");
        json.setContent(new byte[2048]);
        assertEquals(413, run(filter, json).getStatus());

        MockHttpServletRequest upload = request("POST", "/users/me/avatar", "1.1.1.1");
        upload.setContentType("multipart/form-data; boundary=x");
        upload.setContent(new byte[2048]);
        assertEquals(200, run(filter, upload).getStatus());
    }

    @Test
    void disabledFilterNeverBlocks() throws Exception {
        RateLimitFilter off = new RateLimitFilter(false, new Limits(1, 1, 1, 1), 1, new ClientIpResolver(false), null,
                System::nanoTime);
        for (int i = 0; i < 3; i++) {
            assertEquals(200, run(off, request("POST", "/users/authenticate", "1.1.1.1")).getStatus());
        }
    }
}

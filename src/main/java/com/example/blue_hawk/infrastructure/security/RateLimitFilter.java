package com.example.blue_hawk.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.cors.DefaultCorsProcessor;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/**
 * Limite de requisições por IP (token bucket em memória), antes da autenticação: barra força bruta de senha,
 * criação de contas em massa e inundação da API sem gastar BCrypt nem consulta ao banco.
 *
 * <ul>
 *   <li>login ({@code POST /users/authenticate}): {@code app.rate-limit.login-per-minute}</li>
 *   <li>cadastro ({@code POST /users}): {@code app.rate-limit.signup-per-hour}</li>
 *   <li>demais escritas (POST/PUT/PATCH/DELETE): {@code app.rate-limit.write-per-minute}</li>
 *   <li>tudo somado: {@code app.rate-limit.general-per-minute}</li>
 * </ul>
 *
 * <p>Acima do limite responde 429 com {@code Retry-After}. Corpos não-multipart maiores que
 * {@code app.rate-limit.max-body-bytes} recebem 413 (o upload de imagem tem limite próprio no multipart).</p>
 *
 * <p>O contador vive na memória desta instância: com mais de uma réplica, cada uma conta em separado.</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RateLimitFilter extends OncePerRequestFilter {

    /** Limites em requisições por janela; 0 ou menos desliga a regra. */
    public record Limits(int generalPerMinute, int writePerMinute, int loginPerMinute, int signupPerHour) {
    }

    /** Acima disto o mapa é esvaziado: melhor liberar por um instante do que ficar sem memória */
    static final int MAX_BUCKETS = 200_000;

    private static final long CLEANUP_INTERVAL_NANOS = Duration.ofMinutes(1).toNanos();
    private static final long MINUTE_NANOS = Duration.ofMinutes(1).toNanos();
    private static final long HOUR_NANOS = Duration.ofHours(1).toNanos();

    private final boolean enabled;
    private final Limits limits;
    private final long maxBodyBytes;
    private final ClientIpResolver clientIpResolver;
    private final CorsConfigurationSource corsSource;
    private final LongSupplier clock;

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final AtomicLong lastCleanup;

    @Autowired
    public RateLimitFilter(@Value("${app.rate-limit.enabled:true}") boolean enabled,
                           @Value("${app.rate-limit.general-per-minute:600}") int generalPerMinute,
                           @Value("${app.rate-limit.write-per-minute:120}") int writePerMinute,
                           @Value("${app.rate-limit.login-per-minute:10}") int loginPerMinute,
                           @Value("${app.rate-limit.signup-per-hour:20}") int signupPerHour,
                           @Value("${app.rate-limit.max-body-bytes:262144}") long maxBodyBytes,
                           ClientIpResolver clientIpResolver,
                           // Pelo nome: o Spring MVC também registra um CorsConfigurationSource próprio
                           @Qualifier("corsConfigurationSource") ObjectProvider<CorsConfigurationSource> corsSource) {
        this(enabled, new Limits(generalPerMinute, writePerMinute, loginPerMinute, signupPerHour), maxBodyBytes,
                clientIpResolver,
                corsSource.getIfAvailable(), System::nanoTime);
    }

    RateLimitFilter(boolean enabled, Limits limits, long maxBodyBytes, ClientIpResolver clientIpResolver,
                    CorsConfigurationSource corsSource, LongSupplier clock) {
        this.enabled = enabled;
        this.limits = limits;
        this.maxBodyBytes = maxBodyBytes;
        this.clientIpResolver = clientIpResolver;
        this.corsSource = corsSource;
        this.clock = clock;
        this.lastCleanup = new AtomicLong(clock.getAsLong());
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // O preflight não carrega dados nem chega aos controllers
        return !enabled || CorsUtils.isPreFlightRequest(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (bodyTooLarge(request)) {
            reject(request, response, HttpStatus.PAYLOAD_TOO_LARGE, "Request body is too large", 0);
            return;
        }

        long now = clock.getAsLong();
        cleanup(now);

        String ip = clientIpResolver.resolve(request);
        long waitNanos = consume("all|" + ip, limits.generalPerMinute(), MINUTE_NANOS, now);
        if (waitNanos == 0) {
            waitNanos = switch (rule(request)) {
                case LOGIN -> consume("login|" + ip, limits.loginPerMinute(), MINUTE_NANOS, now);
                case SIGNUP -> consume("signup|" + ip, limits.signupPerHour(), HOUR_NANOS, now);
                case WRITE -> consume("write|" + ip, limits.writePerMinute(), MINUTE_NANOS, now);
                case READ -> 0;
            };
        }
        if (waitNanos > 0) {
            long seconds = Math.max(1, (waitNanos + 999_999_999L) / 1_000_000_000L);
            reject(request, response, HttpStatus.TOO_MANY_REQUESTS,
                    "Too many requests. Try again in " + seconds + " seconds.", seconds);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private enum Rule { LOGIN, SIGNUP, WRITE, READ }

    private static Rule rule(HttpServletRequest request) {
        String method = request.getMethod();
        boolean write = "POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method)
                || "DELETE".equals(method);
        if (!write) {
            return Rule.READ;
        }
        if ("POST".equals(method)) {
            String path = path(request);
            if ("/users/authenticate".equals(path)) {
                return Rule.LOGIN;
            }
            if ("/users".equals(path)) {
                return Rule.SIGNUP;
            }
        }
        return Rule.WRITE;
    }

    private static String path(HttpServletRequest request) {
        String path = request.getRequestURI();
        String context = request.getContextPath();
        if (context != null && !context.isEmpty() && path.startsWith(context)) {
            path = path.substring(context.length());
        }
        int params = path.indexOf(';');
        if (params >= 0) {
            path = path.substring(0, params);
        }
        while (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }

    private boolean bodyTooLarge(HttpServletRequest request) {
        if (maxBodyBytes <= 0) {
            return false;
        }
        String contentType = request.getContentType();
        boolean multipart = contentType != null && contentType.toLowerCase().startsWith("multipart/");
        return !multipart && request.getContentLengthLong() > maxBodyBytes;
    }

    /** Devolve 0 se a requisição cabe no limite; senão, quantos nanossegundos faltam para caber. */
    private long consume(String key, int capacity, long windowNanos, long now) {
        if (capacity <= 0) {
            return 0;
        }
        return buckets.computeIfAbsent(key, k -> new Bucket(capacity, windowNanos, now)).tryConsume(now);
    }

    private void cleanup(long now) {
        long last = lastCleanup.get();
        boolean due = now - last >= CLEANUP_INTERVAL_NANOS || buckets.size() > MAX_BUCKETS;
        if (!due || !lastCleanup.compareAndSet(last, now)) {
            return;
        }
        // Balde parado por uma janela inteira já está cheio de novo: recriá-lo depois dá no mesmo
        buckets.values().removeIf(bucket -> bucket.idleFor(now) >= bucket.windowNanos);
        if (buckets.size() > MAX_BUCKETS) {
            buckets.clear();
        }
    }

    private void reject(HttpServletRequest request, HttpServletResponse response, HttpStatus status, String detail,
                        long retryAfterSeconds) throws IOException {
        // Este filtro roda antes do CORS do Spring Security: sem os cabeçalhos, o navegador esconderia o status do front
        if (corsSource != null && CorsUtils.isCorsRequest(request)) {
            CorsConfiguration config = corsSource.getCorsConfiguration(request);
            if (config != null) {
                new DefaultCorsProcessor().processRequest(config, request, response);
                if (response.isCommitted()) {
                    return;
                }
            }
        }
        response.setStatus(status.value());
        if (retryAfterSeconds > 0) {
            response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds));
        }
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"" + status.getReasonPhrase()
                + "\",\"status\":" + status.value() + ",\"detail\":\"" + detail + "\"}");
    }

    /** Token bucket: começa cheio e recarrega {@code capacity} fichas a cada janela. */
    private static final class Bucket {

        private final int capacity;
        private final long windowNanos;
        private double tokens;
        private long last;

        Bucket(int capacity, long windowNanos, long now) {
            this.capacity = capacity;
            this.windowNanos = windowNanos;
            this.tokens = capacity;
            this.last = now;
        }

        synchronized long tryConsume(long now) {
            long elapsed = Math.max(0, now - last);
            tokens = Math.min(capacity, tokens + (double) elapsed * capacity / windowNanos);
            last = now;
            if (tokens >= 1) {
                tokens -= 1;
                return 0;
            }
            return (long) Math.ceil((1 - tokens) * windowNanos / capacity);
        }

        synchronized long idleFor(long now) {
            return now - last;
        }
    }
}

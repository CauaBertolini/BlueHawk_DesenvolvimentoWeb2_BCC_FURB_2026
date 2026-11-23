package com.example.blue_hawk.infrastructure.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tokens já renovados pelo JwtRotationFilter.
 *
 * <p>O token antigo só passa a ser recusado depois de uma janela curta ({@code security.jwt.rotation-grace-seconds}).
 * Sem ela, duas requisições em paralelo com o mesmo token (ou uma requisição que o navegador cancelou depois de
 * enviada) derrubavam a sessão: o servidor renovava o token, o cliente nunca recebia o novo e o antigo já estava
 * invalidado.</p>
 */
@Service
public class TokenBlacklistService {

    private record Entry(Instant activeFrom, Instant expiresAt) {
    }

    private final Map<String, Entry> blacklistedTokens = new ConcurrentHashMap<>();
    private final Duration grace;

    /** Sem janela: o token é recusado assim que entra na lista (usado nos testes). */
    public TokenBlacklistService() {
        this(0);
    }

    @Autowired
    public TokenBlacklistService(@Value("${security.jwt.rotation-grace-seconds:30}") long graceSeconds) {
        this.grace = Duration.ofSeconds(Math.max(0, graceSeconds));
    }

    public void blacklist(String tokenId, Instant expiresAt) {
        if (tokenId != null) {
            Instant expiry = expiresAt != null ? expiresAt : Instant.now().plusSeconds(86400);
            // putIfAbsent: renovar de novo o mesmo token não reinicia a janela
            blacklistedTokens.putIfAbsent(tokenId, new Entry(Instant.now().plus(grace), expiry));
            cleanupExpiredTokens();
        }
    }

    public boolean isBlacklisted(String tokenId) {
        if (tokenId == null) {
            return false;
        }
        Entry entry = blacklistedTokens.get(tokenId);
        if (entry == null) {
            return false;
        }
        Instant now = Instant.now();
        if (entry.expiresAt().isBefore(now)) {
            blacklistedTokens.remove(tokenId);
            return false;
        }
        return !now.isBefore(entry.activeFrom());
    }

    private void cleanupExpiredTokens() {
        Instant now = Instant.now();
        blacklistedTokens.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
    }

    public void clear() {
        blacklistedTokens.clear();
    }
}

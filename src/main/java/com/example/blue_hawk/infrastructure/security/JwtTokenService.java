package com.example.blue_hawk.infrastructure.security;

import com.example.blue_hawk.application.port.TokenIssuer;

import com.example.blue_hawk.domain.entity.user.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class JwtTokenService implements TokenIssuer {

    private final JwtEncoder jwtEncoder;
    private final TokenBlacklistService tokenBlacklistService;
    private final long expirationSeconds;
    private final long rotationMinAgeSeconds;

    @Autowired
    public JwtTokenService(JwtEncoder jwtEncoder,
                           TokenBlacklistService tokenBlacklistService,
                           @Value("${security.jwt.expiration-seconds}") long expirationSeconds,
                           @Value("${security.jwt.rotation-min-age-seconds:60}") long rotationMinAgeSeconds) {
        this.jwtEncoder = jwtEncoder;
        this.tokenBlacklistService = tokenBlacklistService;
        this.expirationSeconds = expirationSeconds;
        this.rotationMinAgeSeconds = Math.max(0, rotationMinAgeSeconds);
    }

    /** Sem idade mínima: todo token é renovado a cada requisição (usado nos testes). */
    public JwtTokenService(JwtEncoder jwtEncoder,
                           TokenBlacklistService tokenBlacklistService,
                           long expirationSeconds) {
        this(jwtEncoder, tokenBlacklistService, expirationSeconds, 0);
    }

    /**
     * Um token recém-emitido não é renovado de novo. Sem isso, cada requisição autenticada assinava um token e
     * deixava outro na lista de recusados por 24 h: um cliente em laço enchia a memória do servidor.
     */
    public boolean shouldRotate(Jwt jwt) {
        Instant issuedAt = jwt.getIssuedAt();
        return rotationMinAgeSeconds == 0 || issuedAt == null
                || !issuedAt.plusSeconds(rotationMinAgeSeconds).isAfter(Instant.now());
    }

    public JwtTokenService(JwtEncoder jwtEncoder,
                           long expirationSeconds) {
        this(jwtEncoder, new TokenBlacklistService(), expirationSeconds);
    }

    @Override
    public String generateToken(User user) {
        Instant now = Instant.now();
        String jti = UUID.randomUUID().toString();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .id(jti)
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(expirationSeconds))
                // O Spring converte "scope" em authorities SCOPE_<valor> (ex.: SCOPE_ADMIN).
                .claim("scope", user.getRole().name())
                .build();

        return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    /**
     * Renova o token gravando o papel atual do usuário (vindo do banco) no claim "scope",
     * em vez de copiar o papel que estava no token antigo.
     */
    public String rotateToken(Jwt currentJwt, String currentRole) {
        return rotate(currentJwt, currentRole);
    }

    public String rotateToken(Jwt currentJwt) {
        return rotate(currentJwt, null);
    }

    private String rotate(Jwt currentJwt, String roleOverride) {
        String identifier = currentJwt.getId() != null ? currentJwt.getId() : currentJwt.getTokenValue();
        Instant expiresAt = currentJwt.getExpiresAt() != null ? currentJwt.getExpiresAt() : Instant.now().plusSeconds(expirationSeconds);
        // Basta um identificador por token (o jti, quando existe): o validador confere os dois
        tokenBlacklistService.blacklist(identifier, expiresAt);

        Instant now = Instant.now();
        String jti = UUID.randomUUID().toString();
        JwtClaimsSet.Builder builder = JwtClaimsSet.builder()
                .id(jti)
                .subject(currentJwt.getSubject())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(expirationSeconds));

        Map<String, Object> claims = currentJwt.getClaims();
        claims.forEach((key, value) -> {
            if (!key.equals("iss") && !key.equals("sub") && !key.equals("exp")
                    && !key.equals("iat") && !key.equals("nbf") && !key.equals("jti")) {
                builder.claim(key, value);
            }
        });
        if (roleOverride != null) {
            builder.claim("scope", roleOverride);
        }

        return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), builder.build()))
                .getTokenValue();
    }

    @Override
    public long getExpirationSeconds() {
        return expirationSeconds;
    }

    public TokenBlacklistService getTokenBlacklistService() {
        return tokenBlacklistService;
    }
}

package com.example.blue_hawk.infrastructure.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Avisa na subida quando o segredo do JWT é o valor de exemplo do repositório (público) ou curto demais:
 * com ele qualquer pessoa assina um token de ADMIN.
 */
@Component
public class JwtSecretCheck {

    static final String EXAMPLE_SECRET = "troque-por-um-segredo-com-pelo-menos-32-caracteres";

    private static final Logger log = LoggerFactory.getLogger(JwtSecretCheck.class);

    public JwtSecretCheck(@Value("${security.jwt.secret}") String secret) {
        if (isWeak(secret)) {
            log.warn("security.jwt.secret is the public example value or shorter than 32 bytes. "
                    + "Set the JWT_SECRET environment variable to a private random value before exposing this API.");
        }
    }

    static boolean isWeak(String secret) {
        return secret == null || EXAMPLE_SECRET.equals(secret)
                || secret.getBytes(StandardCharsets.UTF_8).length < 32;
    }
}

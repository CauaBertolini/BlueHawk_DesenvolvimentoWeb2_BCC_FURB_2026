package com.example.blue_hawk.infrastructure.security;

import com.example.blue_hawk.domain.entity.user.User;
import com.example.blue_hawk.domain.repository.IUserRepository;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;
import java.util.UUID;

/**
 * Monta a autenticação a partir do papel ATUAL do usuário no banco, e não do claim "scope" do token.
 * Como o token é renovado a cada requisição (JwtRotationFilter), confiar no claim manteria um ADMIN
 * rebaixado (ou um usuário removido) com acesso indefinidamente.
 */
public class UserRoleJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    public static final String SCOPE_PREFIX = "SCOPE_";

    private final IUserRepository userRepository;

    public UserRoleJwtAuthenticationConverter(IUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        User user = parseId(jwt.getSubject())
                .flatMap(userRepository::findById)
                .orElseThrow(() -> new BadCredentialsException("User no longer exists"));

        return new JwtAuthenticationToken(
                jwt,
                List.of(new SimpleGrantedAuthority(SCOPE_PREFIX + user.getRole().name())),
                jwt.getSubject());
    }

    private static java.util.Optional<UUID> parseId(String subject) {
        try {
            return java.util.Optional.of(UUID.fromString(subject));
        } catch (IllegalArgumentException | NullPointerException e) {
            return java.util.Optional.empty();
        }
    }
}

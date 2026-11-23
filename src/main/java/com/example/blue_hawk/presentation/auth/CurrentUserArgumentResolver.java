package com.example.blue_hawk.presentation.auth;

import com.example.blue_hawk.application.dto.auth.CurrentUser;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.UUID;

/**
 * Preenche parâmetros {@link CurrentUser} dos controllers a partir do token da requisição
 * ({@code sub} = id do usuário; papel lido do banco pelo UserRoleJwtAuthenticationConverter).
 * Sem token válido, entrega {@link CurrentUser#anonymous()}: cada caso de uso decide se aceita visitantes.
 */
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return CurrentUser.class.equals(parameter.getParameterType());
    }

    @Override
    public CurrentUser resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                       NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        return fromSecurityContext();
    }

    static CurrentUser fromSecurityContext() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwt) || !authentication.isAuthenticated()) {
            return CurrentUser.anonymous();
        }
        boolean admin = jwt.getAuthorities().stream()
                .anyMatch(a -> Roles.ADMIN_AUTHORITY.equals(a.getAuthority()));
        return new CurrentUser(UUID.fromString(jwt.getName()), admin);
    }
}

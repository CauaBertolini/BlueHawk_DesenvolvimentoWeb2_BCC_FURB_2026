package com.example.blue_hawk.application.dto.user;

import com.example.blue_hawk.domain.entity.user.User;

public record UserOutput(
        String id,
        String name,
        String email,
        String role
) {
    /** O que qualquer usuário logado pode ver de outro: só o nome (sem e-mail nem papel) */
    public static UserOutput publicView(User user) {
        return new UserOutput(user.getId().toString(), user.getName(), null, null);
    }

    public static UserOutput from(User user) {
        return new UserOutput(user.getId().toString(), user.getName(), user.getEmail(), user.getRole().name());
    }
}

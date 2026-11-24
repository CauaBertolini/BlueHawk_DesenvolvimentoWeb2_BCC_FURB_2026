package com.example.blue_hawk.application.port;

import com.example.blue_hawk.domain.entity.user.User;

/** Emissão do token de acesso no login (implementado na infraestrutura com JWT). */
public interface TokenIssuer {

    String generateToken(User user);

    long getExpirationSeconds();
}

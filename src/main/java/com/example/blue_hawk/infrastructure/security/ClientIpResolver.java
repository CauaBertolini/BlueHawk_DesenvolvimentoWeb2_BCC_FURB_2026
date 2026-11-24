package com.example.blue_hawk.infrastructure.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HexFormat;

/**
 * IP do cliente, para os limites por IP (requisições HTTP e conexões do WebSocket).
 * Atrás de proxy confiável, é o último endereço do {@code X-Forwarded-For} (o que o proxy acrescentou; os
 * anteriores o cliente pode inventar). IPv6 conta por prefixo /64.
 */
@Component
public class ClientIpResolver {

    private final boolean trustForwardedFor;

    public ClientIpResolver(@Value("${app.rate-limit.trust-forwarded-for:false}") boolean trustForwardedFor) {
        // No Azure App Service a aplicação fica sempre atrás do proxy da plataforma, que acrescenta o IP real ao
        // fim do X-Forwarded-For; sem isso todos os visitantes dividiriam o mesmo contador.
        this.trustForwardedFor = trustForwardedFor || System.getenv("WEBSITE_SITE_NAME") != null;
    }

    public String resolve(HttpServletRequest request) {
        String ip = request.getRemoteAddr();
        if (trustForwardedFor) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                String last = forwarded.substring(forwarded.lastIndexOf(',') + 1).trim();
                if (!last.isEmpty()) {
                    ip = stripPort(last);
                }
            }
        }
        return normalize(ip);
    }

    private static String stripPort(String address) {
        if (address.startsWith("[")) {
            int end = address.indexOf(']');
            return end > 0 ? address.substring(1, end) : address;
        }
        int colon = address.indexOf(':');
        // Um único ":" é IPv4 com porta (formato do Azure); mais de um é IPv6 sem porta
        return colon > 0 && colon == address.lastIndexOf(':') ? address.substring(0, colon) : address;
    }

    /** IPv6 conta por prefixo /64: quem tem um /64 inteiro troca de endereço à vontade. */
    private static String normalize(String ip) {
        if (ip == null) {
            return "unknown";
        }
        if (ip.length() > 45) {
            return ip.substring(0, 45);
        }
        if (ip.indexOf(':') < 0 || !ip.chars().allMatch(c -> c == ':' || c == '.' || Character.digit(c, 16) >= 0)) {
            return ip;
        }
        try {
            // Só literais chegam aqui (caracteres checados acima), então não há consulta DNS
            if (InetAddress.getByName(ip) instanceof Inet6Address v6) {
                return HexFormat.of().formatHex(v6.getAddress(), 0, 8) + "/64";
            }
        } catch (UnknownHostException ignored) {
            // endereço malformado: conta pelo texto mesmo
        }
        return ip;
    }
}

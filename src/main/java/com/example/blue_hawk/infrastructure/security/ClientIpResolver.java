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
            // Formato que não reconhecemos: o App Service também manda o IP, sem porta, em X-Client-IP
            String clientIp = request.getHeader("X-Client-IP");
            if (parse(ip) == null && clientIp != null && parse(clientIp.trim()) != null) {
                ip = clientIp.trim();
            }
        }
        return normalize(ip);
    }

    /**
     * Tira a porta do endereço que o proxy escreveu. O Azure manda o IP com a porta de origem, em mais de um
     * formato: {@code 203.0.113.7:51234}, {@code [2001:db8::1]:51234} e também o IPv4 mapeado em IPv6,
     * {@code ::ffff:203.0.113.7:51234}. Se a porta ficasse, cada conexão nova contaria como outro cliente
     * e o limite por IP nunca seria atingido.
     */
    static String stripPort(String address) {
        if (address.startsWith("[")) {
            int end = address.indexOf(']');
            return end > 0 ? address.substring(1, end) : address;
        }
        if (parse(address) != null) {
            return address;
        }
        int colon = address.lastIndexOf(':');
        boolean portSuffix = colon > 0 && colon < address.length() - 1
                && address.substring(colon + 1).chars().allMatch(Character::isDigit);
        return portSuffix ? address.substring(0, colon) : address;
    }

    /** IPv6 conta por prefixo /64: quem tem um /64 inteiro troca de endereço à vontade. */
    private static String normalize(String ip) {
        if (ip == null) {
            return "unknown";
        }
        InetAddress address = parse(ip);
        if (address instanceof Inet6Address v6) {
            return HexFormat.of().formatHex(v6.getAddress(), 0, 8) + "/64";
        }
        if (address != null) {
            // Também desfaz o IPv4 mapeado em IPv6 (::ffff:a.b.c.d), que o Java já entrega como IPv4
            return address.getHostAddress();
        }
        return ip.length() > 45 ? ip.substring(0, 45) : ip;
    }

    /** O endereço como IP literal, ou null se não for um. Nunca consulta DNS: só aceita o que tem cara de IP. */
    private static InetAddress parse(String ip) {
        boolean v6 = ip.indexOf(':') >= 0;
        boolean literal = v6
                ? ip.chars().allMatch(c -> c == ':' || c == '.' || Character.digit(c, 16) >= 0)
                : ip.matches("\\d{1,3}(\\.\\d{1,3}){3}");
        if (!literal || ip.length() > 45) {
            return null;
        }
        try {
            return InetAddress.getByName(ip);
        } catch (UnknownHostException e) {
            return null;
        }
    }
}

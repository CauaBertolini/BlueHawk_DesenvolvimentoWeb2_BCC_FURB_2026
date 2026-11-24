package com.example.blue_hawk.infrastructure.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ClientIpResolverTest {

    private static MockHttpServletRequest from(String ip) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/boardgames");
        request.setRemoteAddr(ip);
        return request;
    }

    @Test
    void forwardedHeaderIsUsedOnlyWhenTheProxyIsTrusted() {
        MockHttpServletRequest request = from("10.0.0.1");
        // O cliente pode inventar os primeiros endereços; o último é o que o proxy acrescentou (com porta, no Azure)
        request.addHeader("X-Forwarded-For", "9.9.9.9, 203.0.113.7:51234");

        assertEquals("10.0.0.1", new ClientIpResolver(false).resolve(request));
        assertEquals("203.0.113.7", new ClientIpResolver(true).resolve(request));
    }

    private static String behindProxy(String forwardedFor) {
        MockHttpServletRequest request = from("169.254.129.1");
        request.addHeader("X-Forwarded-For", forwardedFor);
        return new ClientIpResolver(true).resolve(request);
    }

    @Test
    void sourcePortNeverMakesTheSameClientLookLikeAnother() {
        // Cada conexão nova chega com outra porta de origem: o cliente tem que continuar sendo o mesmo
        assertEquals("203.0.113.7", behindProxy("203.0.113.7:51234"));
        assertEquals("203.0.113.7", behindProxy("203.0.113.7:40001"));
        assertEquals("203.0.113.7", behindProxy("203.0.113.7"));
        // IPv4 mapeado em IPv6, com e sem porta
        assertEquals("203.0.113.7", behindProxy("::ffff:203.0.113.7:51234"));
        assertEquals("203.0.113.7", behindProxy("::ffff:203.0.113.7:40001"));
        assertEquals("203.0.113.7", behindProxy("::ffff:203.0.113.7"));
        assertEquals("203.0.113.7", behindProxy("[::ffff:203.0.113.7]:51234"));
        // IPv6 entre colchetes, com porta
        assertEquals(behindProxy("[2001:db8:aa:bb::1]:51234"), behindProxy("[2001:db8:aa:bb::2]:40001"));
    }

    @Test
    void unknownForwardedFormatFallsBackToTheClientIpHeader() {
        MockHttpServletRequest request = from("169.254.129.1");
        request.addHeader("X-Forwarded-For", "formato-desconhecido");
        request.addHeader("X-Client-IP", "203.0.113.7");

        assertEquals("203.0.113.7", new ClientIpResolver(true).resolve(request));
        // Sem proxy confiável, nenhum cabeçalho é lido
        assertEquals("169.254.129.1", new ClientIpResolver(false).resolve(request));
    }

    @Test
    void ipv6ClientsAreCountedByTheir64Prefix() {
        ClientIpResolver resolver = new ClientIpResolver(false);
        String a = resolver.resolve(from("2001:db8:aa:bb:1:2:3:4"));
        String b = resolver.resolve(from("2001:db8:aa:bb:ffff:ffff:ffff:ffff"));
        String other = resolver.resolve(from("2001:db8:aa:cc::1"));

        assertEquals(a, b);
        assertNotEquals(a, other);
    }
}

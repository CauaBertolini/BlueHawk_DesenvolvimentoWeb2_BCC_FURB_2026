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

package com.example.blue_hawk.infrastructure.realtime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import com.example.blue_hawk.infrastructure.security.ClientIpResolver;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.List;
import java.util.Map;

/** WebSocket puro (sem STOMP/SockJS) em {@code /ws}, aceitando as mesmas origens do CORS da API */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final RealtimeWebSocketHandler handler;
    private final List<String> allowedOrigins;
    private final ClientIpResolver clientIpResolver;

    public WebSocketConfig(RealtimeWebSocketHandler handler, ClientIpResolver clientIpResolver,
                           @Value("${app.cors.allowed-origins:http://localhost:5173}") List<String> allowedOrigins) {
        this.handler = handler;
        this.clientIpResolver = clientIpResolver;
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws")
                .addInterceptors(new ClientIpInterceptor())
                .setAllowedOrigins(allowedOrigins.toArray(String[]::new));
    }

    /** Guarda o IP real do cliente (o mesmo usado pelo limite de requisições) para o limite de conexões por IP */
    private final class ClientIpInterceptor implements HandshakeInterceptor {

        @Override
        public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                       WebSocketHandler wsHandler, Map<String, Object> attributes) {
            if (request instanceof ServletServerHttpRequest servlet) {
                attributes.put(RealtimeWebSocketHandler.IP_ATTRIBUTE,
                        clientIpResolver.resolve(servlet.getServletRequest()));
            }
            return true;
        }

        @Override
        public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Exception exception) {
            // nada a fazer
        }
    }
}

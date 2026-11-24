package com.example.blue_hawk.infrastructure.realtime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import java.util.List;

/** WebSocket puro (sem STOMP/SockJS) em {@code /ws}, aceitando as mesmas origens do CORS da API */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final RealtimeWebSocketHandler handler;
    private final List<String> allowedOrigins;

    public WebSocketConfig(RealtimeWebSocketHandler handler,
                           @Value("${app.cors.allowed-origins:http://localhost:5173}") List<String> allowedOrigins) {
        this.handler = handler;
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws").setAllowedOrigins(allowedOrigins.toArray(String[]::new));
    }
}

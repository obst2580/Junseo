package com.junseo.realtime;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class RealtimeConfig implements WebSocketConfigurer {

    private final RealtimeHandler handler;

    public RealtimeConfig(RealtimeHandler handler) {
        this.handler = handler;
    }

    /** Any origin: the socket is useless without a valid token in its first frame (no cookies involved). */
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws").setAllowedOriginPatterns("*");
    }
}

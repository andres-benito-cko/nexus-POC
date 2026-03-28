package com.checkout.nexus.api.config;

import com.checkout.nexus.api.service.EventStreamService;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final EventStreamService eventStreamService;

    public WebSocketConfig(EventStreamService eventStreamService) {
        this.eventStreamService = eventStreamService;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(eventStreamService, "/ws")
                .setAllowedOriginPatterns("*");
    }
}

package com.agvlab.rcs.websocket.config;

import com.agvlab.rcs.websocket.handler.AgvWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * 原生 WebSocket 端點註冊設定
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final AgvWebSocketHandler agvWebSocketHandler;

    public WebSocketConfig(AgvWebSocketHandler agvWebSocketHandler) {
        this.agvWebSocketHandler = agvWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 註冊原生 WebSocket 端點 /ws/agv 並允許跨來源存取 (開發階段)
        registry.addHandler(agvWebSocketHandler, "/ws/agv")
                .setAllowedOrigins("*");
    }
}


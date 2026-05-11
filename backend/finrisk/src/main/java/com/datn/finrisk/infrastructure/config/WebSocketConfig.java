package com.datn.finrisk.infrastructure.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

import com.datn.finrisk.web.websocket.*;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    @Autowired
    private LiveEmotionWebSocketHandler liveEmotionWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        System.out.println("🔌 [WS CONFIG] Đang đăng ký handler tại /ws/emotion-stream");
        registry.addHandler(liveEmotionWebSocketHandler, "/ws/emotion-stream")
                .setAllowedOriginPatterns("*");  
    }
        @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(10 * 1024 * 1024); // 10MB
        container.setMaxBinaryMessageBufferSize(10 * 1024 * 1024); // 10MB
        container.setMaxSessionIdleTimeout(60000L); // 60s timeout
        return container;
    }
}
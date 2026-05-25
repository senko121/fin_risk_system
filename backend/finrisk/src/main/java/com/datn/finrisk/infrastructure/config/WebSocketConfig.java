package com.datn.finrisk.infrastructure.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

import com.datn.finrisk.web.websocket.*;

@Slf4j
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    @Autowired
    private LiveEmotionWebSocketHandler liveEmotionWebSocketHandler;

    @Autowired
    private BiometricHandshakeInterceptor biometricHandshakeInterceptor;
    
    @Autowired
    private LiveVoiceWebSocketHandler liveVoiceWebSocketHandler;

        @Override
        public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
            log.info("[WS-CONFIG] Registering handler at /ws/emotion-stream");
            registry.addHandler(liveEmotionWebSocketHandler, "/ws/emotion-stream")
                    .addInterceptors(biometricHandshakeInterceptor)
                    .setAllowedOriginPatterns(
                        "http://localhost:3000",
                        "http://localhost:5173"
                    );

            log.info("[WS-CONFIG] Registering handler at /ws/voice-stream");
            registry.addHandler(liveVoiceWebSocketHandler, "/ws/voice-stream")
                    .addInterceptors(biometricHandshakeInterceptor)
                    .setAllowedOriginPatterns(
                        "http://localhost:3000",
                        "http://localhost:5173"
                    );
        }
        @Bean
        public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(10 * 1024 * 1024);  
        container.setMaxBinaryMessageBufferSize(10 * 1024 * 1024);  
        container.setMaxSessionIdleTimeout(60000L);  
        return container;
    }

}
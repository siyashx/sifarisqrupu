package com.codesupreme.sifarisqrupu.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class ChatWebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic/chat"); // 🔥 Mesajlar için "/topic/chat" kanalını aç
        config.setApplicationDestinationPrefixes("/chat-app"); // ✅ Mesaj gönderme için "/chat-app" prefixi belirle
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws-chat") // ✅ WebSocket bağlantı noktası
                .setAllowedOrigins("*") // 🔥 Tüm domainlerden WebSocket bağlantısını kabul et
                .withSockJS(); // ✅ SockJS desteğini aktif et
    }
}

package org.example.projectCom.configuration;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");          // куда будем слать сообщения (например, /topic/panel-status)
        config.setApplicationDestinationPrefixes("/app"); // откуда будут приходить команды от клиента (например, /app/send-command)
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")                   // базовый URL для подключения
                .setAllowedOriginPatterns("*")       // для локальной разработки
                .withSockJS();                      // SockJS нужен, если браузеры старые или прокси мешают
    }
}

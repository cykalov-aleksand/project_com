package org.example.projectCom.configuration;

import org.example.projectCom.service.AgentSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class AgentWsConfig implements WebSocketConfigurer {

    private final AgentSocketHandler agentSocketHandler;

    public AgentWsConfig(AgentSocketHandler agentSocketHandler) {
        this.agentSocketHandler = agentSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(agentSocketHandler, "/agent-ws")
                .setAllowedOriginPatterns("*");
    }
}

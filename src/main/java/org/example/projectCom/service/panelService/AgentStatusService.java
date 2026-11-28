package org.example.projectCom.service.panelService;


import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AgentStatusService {
    private SimpMessagingTemplate messagingTemplate;
    // Ключ — имя пользователя (principal.getName()), значение — статус
    private final ConcurrentHashMap<String, AgentStatus> agents = new ConcurrentHashMap<>();

    public AgentStatusService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void setConnected(String username) {
        agents.put(username, new AgentStatus(true, LocalDateTime.now()));
        publish(username, true);
    }

    public void setDisconnected(String username) {
        agents.remove(username);
        publish(username, false);
    }

    public AgentStatus getStatus(String username) {
        return agents.getOrDefault(username, new AgentStatus(false, null));
    }

    // Вспомогательный класс (можно сделать статическим внутри сервиса или отдельным DTO)
    public record AgentStatus(boolean connected, LocalDateTime lastSeen) {}
    public record AgentStatusEvent(String username, boolean connected) {}
    private void publish(String username, boolean connected) {
        messagingTemplate.convertAndSend("/topic/agent-status",
                new AgentStatusEvent(username, connected));
    }
}


package org.example.projectCom.service.panelService;


import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AgentStatusService {

    // Ключ — имя пользователя (principal.getName()), значение — статус
    private final ConcurrentHashMap<String, AgentStatus> agents = new ConcurrentHashMap<>();

    public void setConnected(String username) {
        agents.put(username, new AgentStatus(true, LocalDateTime.now()));
    }

    public void setDisconnected(String username) {
        agents.remove(username);
    }

    public AgentStatus getStatus(String username) {
        return agents.getOrDefault(username, new AgentStatus(false, null));
    }

    // Вспомогательный класс (можно сделать статическим внутри сервиса или отдельным DTO)
    public record AgentStatus(boolean connected, LocalDateTime lastSeen) {}
}

package org.example.projectCom.service;


import org.example.projectCom.service.panelService.AgentStatusService;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AgentSocketHandler extends TextWebSocketHandler {

    private final AgentTokenService tokenService;
    private final AgentStatusService agentStatusService;
    // username -> активное соединение агента
    private final Map<String, WebSocketSession> agents = new ConcurrentHashMap<>();

    public AgentSocketHandler(AgentTokenService tokenService, AgentStatusService agentStatusService) {
        this.tokenService = tokenService;
        this.agentStatusService=agentStatusService;
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, @NonNull TextMessage message) throws IOException {
        if (!"true".equals(session.getAttributes().get("auth"))) {
            // первое сообщение агента — токен
            Optional<String> user = tokenService.validate(message.getPayload().trim());
            if (user.isEmpty()) {
                session.close(CloseStatus.POLICY_VIOLATION);
                return;
            }

            String username = user.get();                     // ← вот тут берём имя из токена

            session.getAttributes().put("auth", "true");
            session.getAttributes().put("username", username); // ← тот же ключ, что и в afterConnectionClosed!

            WebSocketSession old = agents.put(username, session); // старое соединение заменяем
            if (old != null && old.isOpen()) old.close();

            agentStatusService.setConnected(username);        // ← статус: подключён
            session.sendMessage(new TextMessage("AUTH_OK:" + username));
            System.out.println("Агент пользователя " + username + " подключён");
            return;
        }

        String user = (String) session.getAttributes().get("username");
        System.out.println("От агента " + user + ": " + message.getPayload());
    }
    // сервер шлёт команду агенту пользователя
    public boolean sendToAgent(String username, String command) {
        WebSocketSession s = agents.get(username);
        if (s == null || !s.isOpen()) return false;
        try {
            synchronized (this) {
                s.sendMessage(new TextMessage(command));
            }
            return true;
        } catch (IOException e) {
            return false;
        }
    }
       @Override
    public void afterConnectionClosed(WebSocketSession session, @NonNull CloseStatus status) {
        String username = (String) session.getAttributes().get("username");
        if (username != null) {
            agentStatusService.setDisconnected(username);
        }
        agents.remove(username);  // подчистить карту, чтобы не держать мёртвую сессию
    }
}

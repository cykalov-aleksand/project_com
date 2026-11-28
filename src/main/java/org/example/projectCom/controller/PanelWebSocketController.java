package org.example.projectCom.controller;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

@Controller
public class PanelWebSocketController {

    @MessageMapping("/panel/status")
    @SendTo("/topic/panel-status")
    public String sendPanelStatus(String payload) {
        System.out.println("Получено: " + payload);
        return "Статус панели: " + payload;
    }
}

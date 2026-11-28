package org.example.projectCom.controller;

import org.example.projectCom.service.AgentSocketHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.Map;

@RestController
public class AgentTestController {

    private final AgentSocketHandler agents;

    public AgentTestController(AgentSocketHandler agents) { this.agents = agents; }

    // команду можно послать только СВОЕМУ агенту — principal.getName()
    @PostMapping("/api/agent/send")
    public Map<String, Object> send(Principal principal, @RequestParam String msg) {
        return Map.of("delivered", agents.sendToAgent(principal.getName(), msg));
    }
}
package org.example.projectCom.controller;

import org.example.projectCom.service.AgentTokenService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.Map;

@RestController
public class AgentTokenController {

    private final AgentTokenService tokens;

    public AgentTokenController(AgentTokenService tokens) { this.tokens = tokens; }

    @PostMapping("/api/agent/token")
    public Map<String, String> issue(Principal principal) {
        return Map.of("token", tokens.issue(principal.getName()));
    }
}
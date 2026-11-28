package org.example.projectCom.service;

import org.example.projectCom.model.AgentToken;

import org.example.projectCom.repository.AgentTokenRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class AgentTokenService {

    private final AgentTokenRepository repo;

    public AgentTokenService(AgentTokenRepository repo) {
        this.repo = repo;
    }

    public String issue(String username) {
        AgentToken t = new AgentToken();
        t.setToken(UUID.randomUUID().toString().replace("-", ""));
        t.setUsername(username);
        t.setExpiresAt(Instant.now().plus(Duration.ofHours(24)));
        repo.save(t);
        return t.getToken();
    }

    public Optional<String> validate(String token) {
        return repo.findByTokenAndExpiresAtAfter(token, Instant.now())
                .map(AgentToken::getUsername);
    }
}

package org.example.projectCom.repository;

import org.example.projectCom.model.AgentToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;

public interface AgentTokenRepository extends JpaRepository<AgentToken, Long> {
    Optional<AgentToken> findByTokenAndExpiresAtAfter(String token, Instant now);
}

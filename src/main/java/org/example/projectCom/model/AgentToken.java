package org.example.projectCom.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
@Getter
@Setter
@Entity
@Table(name = "agent_token")
public class AgentToken {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String token;

    @Column(nullable = false)
    private String username;

    private Instant createdAt = Instant.now();
    private Instant expiresAt;


}

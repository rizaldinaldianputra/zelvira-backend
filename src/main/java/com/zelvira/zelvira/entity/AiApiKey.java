package com.zelvira.zelvira.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "ai_api_keys", indexes = {
        @Index(name = "idx_ai_api_keys_token_hash", columnList = "token_hash", unique = true),
        @Index(name = "idx_ai_api_keys_agent_id", columnList = "agent_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiApiKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id", nullable = false)
    private AiAgent agent;

    @Column(name = "token_hash", nullable = false, unique = true, length = 128)
    private String tokenHash;

    @Column(name = "token_prefix", nullable = false, length = 32)
    private String tokenPrefix;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;
}

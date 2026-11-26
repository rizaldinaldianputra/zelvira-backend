package com.zelvira.zelvira.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "ai_agent_permissions", indexes = {
        @Index(name = "idx_ai_perm_agent_id", columnList = "agent_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_ai_agent_permission", columnNames = {"agent_id", "permission"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiAgentPermission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id", nullable = false)
    private AiAgent agent;

    @Column(nullable = false, length = 100)
    private String permission;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}

package com.zelvira.zelvira.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "ai_audit_logs", indexes = {
        @Index(name = "idx_ai_audit_agent_id", columnList = "agent_id"),
        @Index(name = "idx_ai_audit_created_at", columnList = "created_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "agent_id", length = 100)
    private String agentId;

    @Column(nullable = false, length = 255)
    private String endpoint;

    @Column(name = "http_method", nullable = false, length = 16)
    private String httpMethod;

    @Column(name = "response_status", nullable = false)
    private Integer responseStatus;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "permission_used", length = 100)
    private String permissionUsed;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}

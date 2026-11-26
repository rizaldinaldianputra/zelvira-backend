package com.zelvira.zelvira.repository;

import com.zelvira.zelvira.entity.AiAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiAuditLogRepository extends JpaRepository<AiAuditLog, Long> {
    Page<AiAuditLog> findByAgentIdOrderByCreatedAtDesc(String agentId, Pageable pageable);
}

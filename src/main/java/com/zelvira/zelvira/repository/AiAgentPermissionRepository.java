package com.zelvira.zelvira.repository;

import com.zelvira.zelvira.entity.AiAgentPermission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiAgentPermissionRepository extends JpaRepository<AiAgentPermission, Long> {
    List<AiAgentPermission> findByAgentId(Long agentId);
    void deleteByAgentId(Long agentId);
}

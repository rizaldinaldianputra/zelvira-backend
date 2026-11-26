package com.zelvira.zelvira.repository;

import com.zelvira.zelvira.entity.AiAgent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AiAgentRepository extends JpaRepository<AiAgent, Long> {
    Optional<AiAgent> findByAgentId(String agentId);
    boolean existsByAgentId(String agentId);
    List<AiAgent> findAllByOrderByIdAsc();
}

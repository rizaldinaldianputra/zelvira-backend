package com.zelvira.zelvira.repository;

import com.zelvira.zelvira.entity.AiApiKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AiApiKeyRepository extends JpaRepository<AiApiKey, Long> {
    @Query("SELECT k FROM AiApiKey k JOIN FETCH k.agent WHERE k.tokenHash = :tokenHash")
    Optional<AiApiKey> findByTokenHashWithAgent(@Param("tokenHash") String tokenHash);

    List<AiApiKey> findByAgentIdAndIsActiveTrue(Long agentId);
    Optional<AiApiKey> findFirstByAgentIdAndIsActiveTrueOrderByCreatedAtDesc(Long agentId);
}

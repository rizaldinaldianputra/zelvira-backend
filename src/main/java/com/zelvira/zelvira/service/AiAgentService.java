package com.zelvira.zelvira.service;

import com.zelvira.zelvira.entity.AiAgent;
import com.zelvira.zelvira.entity.AiAgentPermission;
import com.zelvira.zelvira.entity.AiApiKey;
import com.zelvira.zelvira.entity.AiAuditLog;
import com.zelvira.zelvira.repository.AiAgentPermissionRepository;
import com.zelvira.zelvira.repository.AiAgentRepository;
import com.zelvira.zelvira.repository.AiApiKeyRepository;
import com.zelvira.zelvira.repository.AiAuditLogRepository;
import com.zelvira.zelvira.security.ai.AiKeyGenerator;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class AiAgentService {

    private static final Logger log = LoggerFactory.getLogger(AiAgentService.class);

    private final AiAgentRepository agentRepository;
    private final AiApiKeyRepository apiKeyRepository;
    private final AiAgentPermissionRepository permissionRepository;
    private final AiAuditLogRepository auditLogRepository;
    private final AiKeyGenerator keyGenerator;

    @Value("${app.ai.hermes.ceo-key:${HERMES_CEO_API_KEY:}}")
    private String envCeoKey;
    @Value("${app.ai.hermes.content-key:${HERMES_CONTENT_API_KEY:}}")
    private String envContentKey;
    @Value("${app.ai.hermes.marketing-key:${HERMES_MARKETING_API_KEY:}}")
    private String envMarketingKey;
    @Value("${app.ai.hermes.seo-key:${HERMES_SEO_API_KEY:}}")
    private String envSeoKey;
    @Value("${app.ai.hermes.admin-key:${HERMES_ADMIN_API_KEY:}}")
    private String envAdminKey;

    public AiAgentService(
            AiAgentRepository agentRepository,
            AiApiKeyRepository apiKeyRepository,
            AiAgentPermissionRepository permissionRepository,
            AiAuditLogRepository auditLogRepository,
            AiKeyGenerator keyGenerator) {
        this.agentRepository = agentRepository;
        this.apiKeyRepository = apiKeyRepository;
        this.permissionRepository = permissionRepository;
        this.auditLogRepository = auditLogRepository;
        this.keyGenerator = keyGenerator;
    }

    @PostConstruct
    @Transactional
    public void seedDefaultAgents() {
        try {
            seedAgent("hermes-ceo", "Hermes CEO Agent",
                    "Executive read-only agent for business reports, sales, orders, and product overview.",
                    List.of("products.read", "categories.read", "media.read", "sales.read", "orders.read", "dashboard.read", "tasks.manage", "approvals.create"),
                    envCeoKey);

            seedAgent("hermes-content", "Hermes Content Agent",
                    "Content agent for reading product details, media URLs, brand, and content guidelines.",
                    List.of("products.read", "categories.read", "media.read", "brand.read", "content_guidelines.read", "tasks.read", "tasks.update", "approvals.create"),
                    envContentKey);

            seedAgent("hermes-marketing", "Hermes Marketing Agent",
                    "Marketing agent for reading products, pricing, promotions, sales, and analytics.",
                    List.of("products.read", "categories.read", "media.read", "promotions.read", "sales.read", "analytics.read", "tasks.read", "tasks.update", "approvals.create"),
                    envMarketingKey);

            seedAgent("hermes-seo", "Hermes SEO Agent",
                    "SEO agent for reading product descriptions, categories, media, and brand metadata.",
                    List.of("products.read", "categories.read", "media.read", "seo.read", "brand.read", "tasks.read", "tasks.update", "approvals.create"),
                    envSeoKey);

            seedAgent("hermes-admin", "Hermes Admin Agent",
                    "Admin validation agent for reading products, inventory, and product validation.",
                    List.of("products.read", "categories.read", "media.read", "inventory.read", "product_validation.read", "tasks.read", "tasks.update", "approvals.create"),
                    envAdminKey);
        } catch (Exception e) {
            log.warn("Could not seed default Hermes AI agents on startup: {}", e.getMessage());
        }
    }

    private void seedAgent(String agentId, String name, String description, List<String> permissions, String envKey) {
        AiAgent agent = agentRepository.findByAgentId(agentId).orElseGet(() -> {
            AiAgent created = agentRepository.save(AiAgent.builder()
                    .agentId(agentId)
                    .name(name)
                    .description(description)
                    .isActive(true)
                    .build());
            for (String perm : permissions) {
                permissionRepository.save(AiAgentPermission.builder().agent(created).permission(perm).build());
            }
            return created;
        });

        if (envKey != null && !envKey.isBlank() && apiKeyRepository.findByAgentIdAndIsActiveTrue(agent.getId()).isEmpty()) {
            apiKeyRepository.save(AiApiKey.builder()
                    .agent(agent)
                    .tokenHash(keyGenerator.hashToken(envKey.trim()))
                    .tokenPrefix(keyGenerator.extractPrefix(envKey.trim()))
                    .isActive(true)
                    .build());
        }
    }

    public List<Map<String, Object>> listAgents() {
        return agentRepository.findAllByOrderByIdAsc().stream().map(this::toAgentMap).toList();
    }

    public Map<String, Object> getAgent(Long id) {
        AiAgent agent = agentRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("AI Agent not found: " + id));
        return toAgentMap(agent);
    }

    @Transactional
    public Map<String, Object> createAgent(String agentId, String name, String description, List<String> permissions) {
        if (agentId == null || agentId.isBlank()) {
            throw new IllegalArgumentException("agentId is required");
        }
        String cleanId = agentId.trim().toLowerCase();
        if (agentRepository.existsByAgentId(cleanId)) {
            throw new IllegalArgumentException("Agent ID already exists: " + cleanId);
        }

        AiAgent agent = agentRepository.save(AiAgent.builder()
                .agentId(cleanId)
                .name(name != null && !name.isBlank() ? name.trim() : cleanId)
                .description(description)
                .isActive(true)
                .build());

        if (permissions != null) {
            for (String p : permissions) {
                if (p != null && !p.isBlank()) {
                    permissionRepository.save(AiAgentPermission.builder().agent(agent).permission(p.trim()).build());
                }
            }
        }

        String rawToken = issueNewKey(agent);
        Map<String, Object> res = toAgentMap(agent);
        res.put("apiKey", rawToken); // Returned ONCE only on creation
        return res;
    }

    @Transactional
    public Map<String, Object> updateAgent(Long id, String name, String description, Boolean isActive) {
        AiAgent agent = agentRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("AI Agent not found: " + id));
        if (name != null && !name.isBlank()) agent.setName(name.trim());
        if (description != null) agent.setDescription(description);
        if (isActive != null) agent.setIsActive(isActive);
        agentRepository.save(agent);
        return toAgentMap(agent);
    }

    @Transactional
    public Map<String, Object> rotateKey(Long id) {
        AiAgent agent = agentRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("AI Agent not found: " + id));
        revokeActiveKeys(agent);
        String rawToken = issueNewKey(agent);
        Map<String, Object> res = toAgentMap(agent);
        res.put("apiKey", rawToken); // Returned ONCE only on rotation
        return res;
    }

    @Transactional
    public Map<String, Object> revokeKey(Long id) {
        AiAgent agent = agentRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("AI Agent not found: " + id));
        revokeActiveKeys(agent);
        return toAgentMap(agent);
    }

    @Transactional
    public Map<String, Object> updatePermissions(Long id, List<String> permissions) {
        AiAgent agent = agentRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("AI Agent not found: " + id));
        permissionRepository.deleteByAgentId(agent.getId());
        if (permissions != null) {
            for (String p : permissions) {
                if (p != null && !p.isBlank()) {
                    permissionRepository.save(AiAgentPermission.builder().agent(agent).permission(p.trim()).build());
                }
            }
        }
        return toAgentMap(agent);
    }

    public Page<AiAuditLog> getAgentLogs(Long id, int page, int size) {
        AiAgent agent = agentRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("AI Agent not found: " + id));
        return auditLogRepository.findByAgentIdOrderByCreatedAtDesc(
                agent.getAgentId(), PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100)));
    }

    private void revokeActiveKeys(AiAgent agent) {
        List<AiApiKey> active = apiKeyRepository.findByAgentIdAndIsActiveTrue(agent.getId());
        LocalDateTime now = LocalDateTime.now();
        for (AiApiKey k : active) {
            k.setIsActive(false);
            k.setRevokedAt(now);
        }
        apiKeyRepository.saveAll(active);
    }

    private String issueNewKey(AiAgent agent) {
        String raw = keyGenerator.generateRawToken(agent.getAgentId());
        apiKeyRepository.save(AiApiKey.builder()
                .agent(agent)
                .tokenHash(keyGenerator.hashToken(raw))
                .tokenPrefix(keyGenerator.extractPrefix(raw))
                .isActive(true)
                .build());
        return raw;
    }

    private Map<String, Object> toAgentMap(AiAgent agent) {
        List<String> perms = permissionRepository.findByAgentId(agent.getId()).stream()
                .map(AiAgentPermission::getPermission)
                .toList();
        Optional<AiApiKey> activeKey = apiKeyRepository
                .findFirstByAgentIdAndIsActiveTrueOrderByCreatedAtDesc(agent.getId());

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", agent.getId());
        m.put("agentId", agent.getAgentId());
        m.put("name", agent.getName());
        m.put("description", agent.getDescription());
        m.put("isActive", agent.getIsActive());
        m.put("hasActiveKey", activeKey.isPresent());
        m.put("tokenPrefix", activeKey.map(AiApiKey::getTokenPrefix).orElse(null));
        m.put("permissions", perms);
        m.put("lastUsedAt", agent.getLastUsedAt());
        m.put("createdAt", agent.getCreatedAt());
        m.put("updatedAt", agent.getUpdatedAt());
        return m;
    }
}

package com.zelvira.zelvira.controller;

import com.zelvira.zelvira.dto.ApiResponse;
import com.zelvira.zelvira.entity.AiAuditLog;
import com.zelvira.zelvira.service.AiAgentService;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/ai-agents")
@PreAuthorize("hasRole('ADMIN')")
public class AdminAiAgentController {

    private final AiAgentService aiAgentService;

    public AdminAiAgentController(AiAgentService aiAgentService) {
        this.aiAgentService = aiAgentService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> createAgent(@RequestBody Map<String, Object> body) {
        String agentId = body.get("agentId") != null ? body.get("agentId").toString() : null;
        String name = body.get("name") != null ? body.get("name").toString() : null;
        String description = body.get("description") != null ? body.get("description").toString() : null;
        @SuppressWarnings("unchecked")
        List<String> permissions = body.get("permissions") instanceof List<?> list
                ? list.stream().map(Object::toString).toList()
                : List.of();
        return ResponseEntity.ok(ApiResponse.success("AI Agent created",
                aiAgentService.createAgent(agentId, name, description, permissions)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> listAgents() {
        return ResponseEntity.ok(ApiResponse.success("AI Agents fetched", aiAgentService.listAgents()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAgent(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("AI Agent fetched", aiAgentService.getAgent(id)));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateAgent(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        String name = body.get("name") != null ? body.get("name").toString() : null;
        String description = body.get("description") != null ? body.get("description").toString() : null;
        Boolean isActive = body.get("isActive") instanceof Boolean b ? b : null;
        return ResponseEntity.ok(ApiResponse.success("AI Agent updated",
                aiAgentService.updateAgent(id, name, description, isActive)));
    }

    @PostMapping("/{id}/rotate-key")
    public ResponseEntity<ApiResponse<Map<String, Object>>> rotateKey(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("API Key rotated", aiAgentService.rotateKey(id)));
    }

    @PostMapping("/{id}/revoke-key")
    public ResponseEntity<ApiResponse<Map<String, Object>>> revokeKey(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("API Key revoked", aiAgentService.revokeKey(id)));
    }

    @PatchMapping("/{id}/permissions")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updatePermissions(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<String> permissions = body.get("permissions") instanceof List<?> list
                ? list.stream().map(Object::toString).toList()
                : List.of();
        return ResponseEntity.ok(ApiResponse.success("Permissions updated",
                aiAgentService.updatePermissions(id, permissions)));
    }

    @GetMapping("/{id}/logs")
    public ResponseEntity<ApiResponse<Page<AiAuditLog>>> getAgentLogs(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success("Audit logs fetched",
                aiAgentService.getAgentLogs(id, page, size)));
    }
}

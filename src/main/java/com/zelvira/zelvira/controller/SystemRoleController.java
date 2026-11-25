package com.zelvira.zelvira.controller;

import com.zelvira.zelvira.dto.MenuDefinitionResponse;
import com.zelvira.zelvira.dto.SystemRoleRequest;
import com.zelvira.zelvira.dto.SystemRoleResponse;
import com.zelvira.zelvira.service.SystemRoleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/roles")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class SystemRoleController {

    private final SystemRoleService systemRoleService;

    @GetMapping
    public ResponseEntity<List<SystemRoleResponse>> getAllRoles() {
        return ResponseEntity.ok(systemRoleService.getAllRoles());
    }

    @GetMapping("/menus")
    public ResponseEntity<List<MenuDefinitionResponse>> getMenuDefinitions() {
        return ResponseEntity.ok(systemRoleService.getMenuDefinitions());
    }

    @GetMapping("/{id}")
    public ResponseEntity<SystemRoleResponse> getRoleById(@PathVariable Long id) {
        return ResponseEntity.ok(systemRoleService.getRoleById(id));
    }

    @PostMapping
    public ResponseEntity<SystemRoleResponse> createRole(@Valid @RequestBody SystemRoleRequest request) {
        return new ResponseEntity<>(systemRoleService.createRole(request), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<SystemRoleResponse> updateRole(
            @PathVariable Long id,
            @Valid @RequestBody SystemRoleRequest request) {
        return ResponseEntity.ok(systemRoleService.updateRole(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRole(@PathVariable Long id) {
        systemRoleService.deleteRole(id);
        return ResponseEntity.noContent().build();
    }
}

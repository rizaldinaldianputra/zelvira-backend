package com.zelvira.zelvira.controller;

import com.zelvira.zelvira.dto.OrganizationRequest;
import com.zelvira.zelvira.entity.Organization;
import com.zelvira.zelvira.service.OrganizationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/organization")
public class OrganizationController {

    private final OrganizationService organizationService;

    public OrganizationController(OrganizationService organizationService) {
        this.organizationService = organizationService;
    }

    @GetMapping
    public ResponseEntity<Organization> getOrganization() {
        return ResponseEntity.ok(organizationService.getOrganization());
    }

    @PostMapping
    public ResponseEntity<Organization> updateOrganization(@RequestBody OrganizationRequest request) {
        return ResponseEntity.ok(organizationService.updateOrganization(request));
    }
}

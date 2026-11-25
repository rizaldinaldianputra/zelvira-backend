package com.zelvira.zelvira.controller;

import com.zelvira.zelvira.dto.SeoConfigRequest;
import com.zelvira.zelvira.dto.SeoConfigResponse;
import com.zelvira.zelvira.service.SeoConfigService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/seo-configs")
public class SeoConfigController {

    private final SeoConfigService seoConfigService;

    public SeoConfigController(SeoConfigService seoConfigService) {
        this.seoConfigService = seoConfigService;
    }

    @GetMapping
    public ResponseEntity<List<SeoConfigResponse>> getAll() {
        return ResponseEntity.ok(seoConfigService.getAll());
    }

    @GetMapping("/{identifier}")
    public ResponseEntity<SeoConfigResponse> getByIdOrPageName(@PathVariable String identifier) {
        return ResponseEntity.ok(seoConfigService.getByIdOrPageName(identifier));
    }

    @PostMapping
    public ResponseEntity<SeoConfigResponse> save(@RequestBody SeoConfigRequest request) {
        return new ResponseEntity<>(seoConfigService.save(request), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<SeoConfigResponse> update(@PathVariable Long id, @RequestBody SeoConfigRequest request) {
        return ResponseEntity.ok(seoConfigService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        seoConfigService.delete(id);
        return ResponseEntity.noContent().build();
    }
}

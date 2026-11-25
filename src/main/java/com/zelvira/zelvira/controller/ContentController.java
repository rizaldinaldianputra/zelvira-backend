package com.zelvira.zelvira.controller;

import com.zelvira.zelvira.dto.ContentItemRequest;
import com.zelvira.zelvira.dto.ContentItemResponse;
import com.zelvira.zelvira.service.ContentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/content/items")
public class ContentController {

    private final ContentService contentService;

    public ContentController(ContentService contentService) {
        this.contentService = contentService;
    }

    @PostMapping
    public ResponseEntity<ContentItemResponse> createItem(@RequestBody ContentItemRequest request) {
        return ResponseEntity.ok(contentService.createItem(request));
    }

    @GetMapping
    public ResponseEntity<List<ContentItemResponse>> getAllItems(
            @RequestParam(required = false) String platform) {
        return ResponseEntity.ok(contentService.getAllItems(platform));
    }

    @GetMapping("/type/{type}")
    public ResponseEntity<List<ContentItemResponse>> getItemsByType(
            @PathVariable String type,
            @RequestParam(required = false) String platform,
            @RequestParam(defaultValue = "false") boolean activeOnly) {
        return ResponseEntity.ok(contentService.getItemsByType(type, platform, activeOnly));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ContentItemResponse> updateItem(@PathVariable Long id,
            @RequestBody ContentItemRequest request) {
        return ResponseEntity.ok(contentService.updateItem(id, request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ContentItemResponse> getById(@PathVariable String id) {
        return ResponseEntity.ok(contentService.getById(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteItem(@PathVariable Long id) {
        contentService.deleteItem(id);
        return ResponseEntity.noContent().build();
    }
}

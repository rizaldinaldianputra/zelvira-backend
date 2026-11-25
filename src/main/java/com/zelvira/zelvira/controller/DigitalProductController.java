package com.zelvira.zelvira.controller;

import com.zelvira.zelvira.entity.DigitalCategory;
import com.zelvira.zelvira.entity.DigitalProduct;
import com.zelvira.zelvira.service.DigitalProductService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/products/digital")
public class DigitalProductController {

    private final DigitalProductService digitalProductService;

    public DigitalProductController(DigitalProductService digitalProductService) {
        this.digitalProductService = digitalProductService;
    }

    // Admin only endpoint
    @PostMapping("/sync")
    public ResponseEntity<?> syncProducts(@RequestBody(required = false) Map<String, String> payload) {
        String type = payload != null ? payload.get("type") : "prepaid";
        int count = 0;

        if ("all".equalsIgnoreCase(type)) {
            count += digitalProductService.syncPrepaidProducts();
            count += digitalProductService.syncPostpaidProducts();
        } else if ("pasca".equalsIgnoreCase(type) || "postpaid".equalsIgnoreCase(type)) {
            count = digitalProductService.syncPostpaidProducts();
        } else {
            count = digitalProductService.syncPrepaidProducts();
        }
        return ResponseEntity.ok(Map.of(
                "message",
                "Data lama berhasil dibersihkan dan " + count + " produk baru berhasil disinkronisasi untuk " + type,
                "count", count,
                "type", type));
    }

    @GetMapping
    public ResponseEntity<List<DigitalProduct>> getProducts(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Boolean isPostpaid,
            @RequestParam(required = false, defaultValue = "false") Boolean includeInactive) {
        return ResponseEntity.ok(digitalProductService.getProducts(category, isPostpaid, includeInactive));
    }

    @GetMapping("/categories")
    public ResponseEntity<List<String>> getCategories(
            @RequestParam(required = false) Boolean isPostpaid,
            @RequestParam(required = false, defaultValue = "true") Boolean onlyActive) {
        return ResponseEntity.ok(digitalProductService.getAllCategories(isPostpaid, onlyActive));
    }

    @GetMapping("/categories-list")
    public ResponseEntity<List<DigitalCategory>> getCategoriesList(
            @RequestParam(required = false) Boolean isPostpaid,
            @RequestParam(required = false) Boolean onlyActive) {
        return ResponseEntity.ok(digitalProductService.getCategoryList(isPostpaid, onlyActive));
    }

    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> getConfig() {
        return ResponseEntity.ok(digitalProductService.getDigitalProductConfig());
    }

    @PutMapping("/config")
    public ResponseEntity<Map<String, Object>> updateConfig(@RequestBody Map<String, Object> payload) {
        return ResponseEntity.ok(digitalProductService.updateDigitalProductConfig(payload));
    }

    @PutMapping("/{id}/toggle")
    public ResponseEntity<?> toggleProductStatus(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Boolean> payload) {
        Boolean active = payload != null ? payload.get("active") : null;
        return ResponseEntity.ok(digitalProductService.toggleProductStatus(id, active));
    }

    @PutMapping("/categories/{id}/toggle")
    public ResponseEntity<?> toggleCategoryStatus(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Boolean> payload) {
        Boolean active = payload != null ? payload.get("active") : null;
        return ResponseEntity.ok(digitalProductService.toggleCategoryStatus(id, active));
    }

    @PutMapping("/categories/{id}")
    public ResponseEntity<?> updateCategory(
            @PathVariable Long id,
            @RequestBody Map<String, Object> payload) {
        return ResponseEntity.ok(digitalProductService.updateCategory(id, payload));
    }

    @GetMapping("/{sku}")
    public ResponseEntity<DigitalProduct> getProductBySku(@PathVariable String sku) {
        return ResponseEntity.ok(digitalProductService.getProductBySku(sku));
    }
}

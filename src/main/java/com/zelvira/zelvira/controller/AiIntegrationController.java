package com.zelvira.zelvira.controller;

import com.zelvira.zelvira.dto.ai.AiApiResponse;
import com.zelvira.zelvira.dto.ai.AiPageResponse;
import com.zelvira.zelvira.dto.ai.AiProductResponse;
import com.zelvira.zelvira.security.ai.AiPermission;
import com.zelvira.zelvira.security.ai.AiProfile;
import com.zelvira.zelvira.security.ai.HermesAiAuthFilter;
import com.zelvira.zelvira.service.AiIntegrationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/v1/ai")
public class AiIntegrationController {

    private final AiIntegrationService aiService;

    public AiIntegrationController(AiIntegrationService aiService) {
        this.aiService = aiService;
    }

    @GetMapping("/health")
    public ResponseEntity<AiApiResponse<Map<String, Object>>> health(HttpServletRequest request) {
        AiProfile profile = getProfile(request);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "UP");
        data.put("mode", "READ_ONLY");
        data.put("profile", profile != null ? profile.getId() : null);
        return ResponseEntity.ok(AiApiResponse.ok(data, profile != null ? profile.getId() : null));
    }

    @GetMapping("/capabilities")
    public ResponseEntity<AiApiResponse<Map<String, Object>>> capabilities(HttpServletRequest request) {
        AiProfile profile = getProfile(request);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("profile", profile.getId());
        data.put("permissions", profile.getPermissions().stream().map(Enum::name).toList());
        data.put("readOnly", true);
        data.put("maxPageSize", 50);
        return ResponseEntity.ok(AiApiResponse.ok(data, profile.getId()));
    }

    @GetMapping("/products")
    public ResponseEntity<AiApiResponse<AiPageResponse<AiProductResponse>>> getProducts(
            HttpServletRequest request,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String color,
            @RequestParam(required = false) String size,
            @RequestParam(required = false) String q) {
        AiProfile profile = requirePermission(request, AiPermission.READ_PRODUCTS);
        return ResponseEntity.ok(AiApiResponse.ok(
                aiService.getProducts(page, limit, sortBy, sortDir, status, categoryId, color, size, q),
                profile.getId()));
    }

    @GetMapping("/products/search")
    public ResponseEntity<AiApiResponse<AiPageResponse<AiProductResponse>>> searchProducts(
            HttpServletRequest request,
            @RequestParam String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir,
            @RequestParam(required = false) String status) {
        AiProfile profile = requirePermission(request, AiPermission.READ_PRODUCTS);
        if (q == null || q.isBlank()) {
            return ResponseEntity.badRequest().body(AiApiResponse.error("Query parameter 'q' is required", profile.getId()));
        }
        return ResponseEntity.ok(AiApiResponse.ok(
                aiService.getProducts(page, limit, sortBy, sortDir, status, null, null, null, q),
                profile.getId()));
    }

    @GetMapping("/products/{id}")
    public ResponseEntity<AiApiResponse<AiProductResponse>> getProductById(
            HttpServletRequest request,
            @PathVariable Long id) {
        AiProfile profile = requirePermission(request, AiPermission.READ_PRODUCTS);
        return ResponseEntity.ok(AiApiResponse.ok(aiService.getProductById(id), profile.getId()));
    }

    @GetMapping("/products/{id}/variants")
    public ResponseEntity<AiApiResponse<List<AiProductResponse.VariantSummary>>> getProductVariants(
            HttpServletRequest request,
            @PathVariable Long id) {
        AiProfile profile = requirePermission(request, AiPermission.READ_PRODUCTS);
        return ResponseEntity.ok(AiApiResponse.ok(aiService.getProductVariants(id), profile.getId()));
    }

    @GetMapping("/products/{id}/images")
    public ResponseEntity<AiApiResponse<List<AiProductResponse.ImageSummary>>> getProductImages(
            HttpServletRequest request,
            @PathVariable Long id) {
        AiProfile profile = requirePermission(request, AiPermission.READ_PRODUCTS);
        return ResponseEntity.ok(AiApiResponse.ok(aiService.getProductImages(id), profile.getId()));
    }

    @GetMapping("/categories")
    public ResponseEntity<AiApiResponse<List<Map<String, Object>>>> getCategories(HttpServletRequest request) {
        AiProfile profile = requirePermission(request, AiPermission.READ_CATEGORIES);
        return ResponseEntity.ok(AiApiResponse.ok(aiService.getCategories(), profile.getId()));
    }

    @GetMapping("/categories/{id}/products")
    public ResponseEntity<AiApiResponse<AiPageResponse<AiProductResponse>>> getCategoryProducts(
            HttpServletRequest request,
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int limit) {
        AiProfile profile = requirePermission(request, AiPermission.READ_CATEGORIES);
        return ResponseEntity.ok(AiApiResponse.ok(
                aiService.getProducts(page, limit, "id", "asc", null, id, null, null, null),
                profile.getId()));
    }

    @GetMapping("/brand")
    public ResponseEntity<AiApiResponse<Map<String, Object>>> getBrand(HttpServletRequest request) {
        AiProfile profile = requirePermission(request, AiPermission.READ_BRAND);
        return ResponseEntity.ok(AiApiResponse.ok(aiService.getBrandInfo(), profile.getId()));
    }

    @GetMapping("/dashboard/summary")
    public ResponseEntity<AiApiResponse<Map<String, Object>>> getDashboardSummary(HttpServletRequest request) {
        AiProfile profile = requirePermission(request, AiPermission.READ_DASHBOARD);
        return ResponseEntity.ok(AiApiResponse.ok(aiService.getDashboardSummary(), profile.getId()));
    }

    @GetMapping("/sales/summary")
    public ResponseEntity<AiApiResponse<Map<String, Object>>> getSalesSummary(HttpServletRequest request) {
        AiProfile profile = requirePermission(request, AiPermission.READ_SALES);
        return ResponseEntity.ok(AiApiResponse.ok(aiService.getSalesSummary(), profile.getId()));
    }

    @GetMapping("/orders/summary")
    public ResponseEntity<AiApiResponse<Map<String, Object>>> getOrdersSummary(HttpServletRequest request) {
        AiProfile profile = requirePermission(request, AiPermission.READ_ORDERS);
        return ResponseEntity.ok(AiApiResponse.ok(aiService.getOrdersSummary(), profile.getId()));
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<AiApiResponse<Object>> handleForbidden(SecurityException ex, HttpServletRequest req) {
        AiProfile p = getProfile(req);
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(AiApiResponse.error(ex.getMessage(), p != null ? p.getId() : null));
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<AiApiResponse<Object>> handleNotFound(NoSuchElementException ex, HttpServletRequest req) {
        AiProfile p = getProfile(req);
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(AiApiResponse.error(ex.getMessage(), p != null ? p.getId() : null));
    }

    private AiProfile getProfile(HttpServletRequest request) {
        return (AiProfile) request.getAttribute(HermesAiAuthFilter.ATTR_AI_PROFILE);
    }

    private AiProfile requirePermission(HttpServletRequest request, AiPermission permission) {
        AiProfile profile = getProfile(request);
        if (profile == null) {
            throw new SecurityException("Unauthenticated AI profile");
        }
        if (!profile.hasPermission(permission)) {
            throw new SecurityException("Profile '" + profile.getId() + "' lacks permission: " + permission.name());
        }
        return profile;
    }
}

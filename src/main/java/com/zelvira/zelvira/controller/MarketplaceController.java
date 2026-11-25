package com.zelvira.zelvira.controller;

import com.zelvira.zelvira.entity.Product;
import com.zelvira.zelvira.repository.ProductRepository;
import com.zelvira.zelvira.service.MarketplaceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping({"/api/marketplace", "/api/v1/marketplace"})
@Slf4j
public class MarketplaceController {

    private final List<MarketplaceService> marketplaceServices;
    private final ProductRepository productRepository;

    public MarketplaceController(List<MarketplaceService> marketplaceServices, ProductRepository productRepository) {
        this.marketplaceServices = marketplaceServices;
        this.productRepository = productRepository;
    }

    private MarketplaceService getService(String type) {
        return marketplaceServices.stream()
                .filter(service -> service.getMarketplaceType().equalsIgnoreCase(type))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported marketplace type: " + type));
    }

    @GetMapping("/auth-url/{type}")
    public ResponseEntity<Map<String, String>> getAuthUrl(@PathVariable String type) {
        String authUrl = getService(type).getAuthUrl();
        return ResponseEntity.ok(Map.of("url", authUrl));
    }

    @RequestMapping(value = "/callback/{type}", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<Map<String, Object>> processCallback(
            @PathVariable String type,
            @RequestParam String code,
            @RequestParam(required = false) String shopId) {

        getService(type).processAuthCallback(code, shopId);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Otorisasi berhasil untuk " + type.toUpperCase()
        ));
    }

    @GetMapping("/shops/{type}")
    public ResponseEntity<List<Map<String, Object>>> getAuthorizedShops(@PathVariable String type) {
        List<Map<String, Object>> shops = getService(type).getAuthorizedShops();
        return ResponseEntity.ok(shops);
    }

    @DeleteMapping("/shops/{type}/{shopId}")
    public ResponseEntity<Map<String, Object>> disconnectShop(
            @PathVariable String type,
            @PathVariable String shopId) {

        getService(type).disconnectShop(shopId);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Koneksi toko " + shopId + " berhasil diputuskan"
        ));
    }

    @PostMapping("/shops/{type}/sync")
    public ResponseEntity<Map<String, Object>> syncShopCipher(
            @PathVariable String type,
            @RequestParam(required = false) String shopId) {
        Map<String, Object> result = getService(type).syncShopCipher(shopId);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/push/{type}/{productId}")
    public ResponseEntity<Map<String, Object>> pushProduct(
            @PathVariable String type,
            @PathVariable Long productId) {

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found with ID: " + productId));

        getService(type).pushProduct(product);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Produk '" + product.getName() + "' berhasil diproses sinkronisasi ke " + type.toUpperCase()
        ));
    }

    @GetMapping("/products/{type}")
    public ResponseEntity<Map<String, Object>> getRemoteProducts(
            @PathVariable String type,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String pageToken) {

        Map<String, Object> result = getService(type).getRemoteProducts(pageSize, pageToken);
        return ResponseEntity.ok(result);
    }

    @PutMapping("/products/{type}/{remoteProductId}")
    public ResponseEntity<Map<String, Object>> updateRemoteProduct(
            @PathVariable String type,
            @PathVariable String remoteProductId,
            @RequestBody Map<String, Object> payload) {

        Map<String, Object> result = getService(type).updateRemoteProduct(remoteProductId, payload);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/products/{type}/{remoteProductId}/activate")
    public ResponseEntity<Map<String, Object>> activateRemoteProduct(
            @PathVariable String type,
            @PathVariable String remoteProductId) {
        Map<String, Object> result = getService(type).activateRemoteProduct(remoteProductId);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/products/{type}/{remoteProductId}/deactivate")
    public ResponseEntity<Map<String, Object>> deactivateRemoteProduct(
            @PathVariable String type,
            @PathVariable String remoteProductId) {
        Map<String, Object> result = getService(type).deactivateRemoteProduct(remoteProductId);
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/products/{type}/{remoteProductId}")
    public ResponseEntity<Map<String, Object>> deleteRemoteProduct(
            @PathVariable String type,
            @PathVariable String remoteProductId) {
        Map<String, Object> result = getService(type).deleteRemoteProduct(remoteProductId);
        return ResponseEntity.ok(result);
    }

    @PostMapping(value = "/upload-image/{type}", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadImage(
            @PathVariable String type,
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        Map<String, Object> result = getService(type).uploadImage(file);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/orders/{type}")
    public ResponseEntity<Map<String, Object>> getRemoteOrders(
            @PathVariable String type,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String pageToken,
            @RequestParam(required = false) String orderStatus) {

        Map<String, Object> result = getService(type).getRemoteOrders(pageSize, pageToken, orderStatus);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/orders/{type}/sync")
    public ResponseEntity<Map<String, Object>> syncRemoteOrders(@PathVariable String type) {
        Map<String, Object> result = getService(type).syncRemoteOrders();
        return ResponseEntity.ok(result);
    }

    @PostMapping("/orders/{type}/{orderId}/deduct-stock")
    public ResponseEntity<Map<String, Object>> deductStockForOrder(
            @PathVariable String type,
            @PathVariable String orderId) {

        Map<String, Object> result = getService(type).deductStockForOrder(orderId);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/products/{type}/{productId}/sync-stock")
    public ResponseEntity<Map<String, Object>> syncSingleProductStock(
            @PathVariable String type,
            @PathVariable Long productId) {

        Map<String, Object> result = getService(type).syncSingleProductStock(productId);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/products/{type}/sync-all-stocks")
    public ResponseEntity<Map<String, Object>> syncAllProductStocks(@PathVariable String type) {
        Map<String, Object> result = getService(type).syncAllProductStocks();
        return ResponseEntity.ok(result);
    }

    @GetMapping("/chat/{type}/conversations")
    public ResponseEntity<Map<String, Object>> getChatConversations(
            @PathVariable String type,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String pageToken) {
        Map<String, Object> result = getService(type).getChatConversations(pageSize, pageToken);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/chat/{type}/conversations/{conversationId}/messages")
    public ResponseEntity<Map<String, Object>> getChatMessages(
            @PathVariable String type,
            @PathVariable String conversationId,
            @RequestParam(defaultValue = "50") int pageSize,
            @RequestParam(required = false) String pageToken) {
        Map<String, Object> result = getService(type).getChatMessages(conversationId, pageSize, pageToken);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/chat/{type}/conversations/{conversationId}/messages")
    public ResponseEntity<Map<String, Object>> sendChatMessage(
            @PathVariable String type,
            @PathVariable String conversationId,
            @RequestBody Map<String, Object> body) {
        String content = body.get("content") != null ? String.valueOf(body.get("content")) : "";
        String messageType = body.get("type") != null ? String.valueOf(body.get("type")) : "TEXT";
        Map<String, Object> result = getService(type).sendChatMessage(conversationId, content, messageType);
        return ResponseEntity.ok(result);
    }

    @PostMapping(value = {"/webhook/{type}", "/{type}/webhook"})
    public ResponseEntity<Map<String, Object>> handleWebhook(
            @PathVariable String type,
            @RequestBody(required = false) Map<String, Object> payload,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "x-tts-sign", required = false) String ttsSign) {

        log.info("Received TikTok/Marketplace webhook event for {}: payload={}", type, payload);

        try {
            // Auto-trigger sync when order status change webhook is received
            if ("tiktok".equalsIgnoreCase(type)) {
                getService(type).syncRemoteOrders();
            }
        } catch (Exception e) {
            log.warn("Error processing TikTok webhook event: {}", e.getMessage());
        }

        return ResponseEntity.ok(Map.of(
                "code", 0,
                "message", "success"
        ));
    }
}

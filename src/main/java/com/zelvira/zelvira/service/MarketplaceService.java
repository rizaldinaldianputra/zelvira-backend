package com.zelvira.zelvira.service;

import com.zelvira.zelvira.entity.Product;

public interface MarketplaceService {
    String getMarketplaceType();

    // Auth & Token Management
    String getAuthUrl();

    void processAuthCallback(String code, String shopId);

    default java.util.List<java.util.Map<String, Object>> getAuthorizedShops() {
        return java.util.Collections.emptyList();
    }

    default void disconnectShop(String shopId) {
    }

    // Product Sync
    void pushProduct(Product product);

    void updateProduct(Product product);

    void updateStock(Product product, Integer newStock);

    void deleteProduct(Product product);

    default java.util.Map<String, Object> getRemoteProducts(int pageSize, String pageToken) {
        return java.util.Collections.emptyMap();
    }

    default java.util.Map<String, Object> updateRemoteProduct(String remoteProductId, java.util.Map<String, Object> payload) {
        return java.util.Collections.emptyMap();
    }

    default java.util.Map<String, Object> syncShopCipher(String shopId) {
        return java.util.Collections.emptyMap();
    }

    default java.util.Map<String, Object> activateRemoteProduct(String remoteProductId) {
        return java.util.Collections.emptyMap();
    }

    default java.util.Map<String, Object> deactivateRemoteProduct(String remoteProductId) {
        return java.util.Collections.emptyMap();
    }

    default java.util.Map<String, Object> deleteRemoteProduct(String remoteProductId) {
        return java.util.Collections.emptyMap();
    }

    default java.util.Map<String, Object> uploadImage(org.springframework.web.multipart.MultipartFile file) {
        return java.util.Collections.emptyMap();
    }

    // Orders & Stock Synchronization
    default java.util.Map<String, Object> getRemoteOrders(int pageSize, String pageToken, String orderStatus) {
        return java.util.Collections.emptyMap();
    }

    default java.util.Map<String, Object> syncRemoteOrders() {
        return java.util.Collections.emptyMap();
    }

    default java.util.Map<String, Object> deductStockForOrder(String orderId) {
        return java.util.Collections.emptyMap();
    }

    default java.util.Map<String, Object> syncAllProductStocks() {
        return java.util.Collections.emptyMap();
    }

    default java.util.Map<String, Object> syncSingleProductStock(Long productId) {
        return java.util.Collections.emptyMap();
    }

    // Customer Service & Chat
    default java.util.Map<String, Object> getChatConversations(int pageSize, String pageToken) {
        return java.util.Collections.emptyMap();
    }

    default java.util.Map<String, Object> getChatMessages(String conversationId, int pageSize, String pageToken) {
        return java.util.Collections.emptyMap();
    }

    default java.util.Map<String, Object> sendChatMessage(String conversationId, String content, String messageType) {
        return java.util.Collections.emptyMap();
    }
}

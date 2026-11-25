package com.zelvira.zelvira.service.impl;

import com.zelvira.zelvira.entity.Product;
import com.zelvira.zelvira.service.MarketplaceService;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class ShopeeApiServiceImpl implements MarketplaceService {

    @Override
    public String getMarketplaceType() {
        return "SHOPEE";
    }

    @Override
    public String getAuthUrl() {
        // Construct Shopee OAuth URL
        log.info("Constructing Shopee Auth URL");
        return "https://partner.shopeemobile.com/api/v2/shop/auth_partner?partner_id=MOCK_PARTNER&redirect=MOCK_REDIRECT";
    }

    @Override
    public void processAuthCallback(String code, String shopId) {
        log.info("Processing Shopee Auth Callback with code: {} and shopId: {}", code, shopId);
        // Exchange code for access token
    }

    @Override
    public void pushProduct(Product product) {
        log.info("Pushing product to Shopee: {}", product.getName());
        // Map product to Shopee format and push via API
    }

    @Override
    public void updateProduct(Product product) {
        log.info("Updating product in Shopee: {}", product.getName());
        // Map product to Shopee format and update via API
    }

    @Override
    public void updateStock(Product product, Integer newStock) {
        log.info("Updating stock for product in Shopee: {} to {}", product.getName(), newStock);
        // Update stock via API
    }

    @Override
    public void deleteProduct(Product product) {
        log.info("Deleting product from Shopee: {}", product.getName());
        // Delete or unlist product via API
    }

    @Override
    public java.util.Map<String, Object> getChatConversations(int pageSize, String pageToken) {
        return java.util.Map.of(
                "success", true,
                "marketplace", "SHOPEE",
                "connected", false,
                "message", "Shopee Seller Chat siap dihubungkan melalui Shopee Open Platform Partner API.",
                "conversations", java.util.Collections.emptyList()
        );
    }

    @Override
    public java.util.Map<String, Object> getChatMessages(String conversationId, int pageSize, String pageToken) {
        return java.util.Map.of(
                "success", true,
                "marketplace", "SHOPEE",
                "connected", false,
                "messages", java.util.Collections.emptyList()
        );
    }

    @Override
    public java.util.Map<String, Object> sendChatMessage(String conversationId, String content, String messageType) {
        return java.util.Map.of(
                "success", false,
                "message", "Shopee Seller Chat belum aktif di Shopee Open Platform."
        );
    }
}

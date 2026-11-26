package com.zelvira.zelvira.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zelvira.zelvira.entity.MarketplaceConfig;
import com.zelvira.zelvira.entity.Product;
import com.zelvira.zelvira.repository.MarketplaceConfigRepository;
import com.zelvira.zelvira.service.MarketplaceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

@Service
@Slf4j
public class InstagramApiServiceImpl implements MarketplaceService {

    @Value("${app.instagram.app-id:}")
    private String appId;

    @Value("${app.instagram.app-secret:}")
    private String appSecret;

    @Value("${app.instagram.config-id:}")
    private String configId;

    @Value("${app.instagram.business-id:}")
    private String businessId;

    @Value("${app.instagram.account-id:}")
    private String defaultAccountId;

    @Value("${app.instagram.account-username:zelviraofficialstore}")
    private String defaultAccountUsername;

    @Value("${app.instagram.webhook-verify-token:zelvira_ig_verify_token_2026}")
    private String webhookVerifyToken;

    @Value("${app.instagram.redirect-uri:https://api.zelvira.co.id/api/v1/callbacks/instagram/oauth}")
    private String redirectUri;

    @Value("${app.instagram.admin-redirect-url:https://admin.zelvira.co.id/admin/integrations/instagram}")
    private String adminRedirectUrl;

    private final MarketplaceConfigRepository configRepository;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public InstagramApiServiceImpl(MarketplaceConfigRepository configRepository,
                                  ObjectMapper objectMapper) {
        this.configRepository = configRepository;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    @Override
    public String getMarketplaceType() {
        return "INSTAGRAM";
    }

    public String getWebhookVerifyToken() {
        return webhookVerifyToken;
    }

    public String getAdminRedirectUrl() {
        return adminRedirectUrl;
    }

    public String getDefaultAccountId() {
        return defaultAccountId;
    }

    public String getDefaultAccountUsername() {
        return defaultAccountUsername;
    }

    @Override
    public String getAuthUrl() {
        // Facebook Login for Business with Instagram Asset (Unified Login)
        // Using config_id=1482133923808500
        String cleanRedirectUri = redirectUri.trim();
        String encodedRedirectUri = URLEncoder.encode(cleanRedirectUri, StandardCharsets.UTF_8);

        String authUrl = String.format(
                "https://www.facebook.com/v21.0/dialog/oauth?client_id=%s&config_id=%s&redirect_uri=%s&response_type=code&scope=instagram_basic,instagram_content_publish,instagram_manage_comments,instagram_manage_messages,pages_show_list,pages_read_engagement",
                appId.trim(),
                configId.trim(),
                encodedRedirectUri
        );

        log.info("Generated Instagram Unified Login authorization URL: {}", authUrl);
        return authUrl;
    }

    @Override
    @Transactional
    public void processAuthCallback(String code, String shopId) {
        log.info("Processing Instagram Auth Callback with code: {} and shopId: {}", code, shopId);
        try {
            // 1. Exchange authorization code for short-lived access token
            String tokenExchangeUrl = "https://graph.facebook.com/v21.0/oauth/access_token";
            String requestBody = String.format(
                    "client_id=%s&client_secret=%s&redirect_uri=%s&code=%s&grant_type=authorization_code",
                    URLEncoder.encode(appId.trim(), StandardCharsets.UTF_8),
                    URLEncoder.encode(appSecret != null ? appSecret.trim() : "", StandardCharsets.UTF_8),
                    URLEncoder.encode(redirectUri.trim(), StandardCharsets.UTF_8),
                    URLEncoder.encode(code.trim(), StandardCharsets.UTF_8)
            );

            HttpRequest tokenRequest = HttpRequest.newBuilder()
                    .uri(URI.create(tokenExchangeUrl))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(20))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> tokenResponse = httpClient.send(tokenRequest, HttpResponse.BodyHandlers.ofString());
            log.info("Instagram token exchange response code: {}, body: {}", tokenResponse.statusCode(), tokenResponse.body());

            JsonNode tokenJson = objectMapper.readTree(tokenResponse.body());
            if (tokenJson.has("error")) {
                String errorMsg = tokenJson.path("error").path("message").asText("Unknown error");
                throw new RuntimeException("Gagal menukarkan authorization code Instagram: " + errorMsg);
            }

            String shortLivedToken = tokenJson.path("access_token").asText();
            long expiresIn = tokenJson.path("expires_in").asLong(3600);

            // 2. Exchange short-lived token for long-lived token (60 days)
            String finalAccessToken = shortLivedToken;
            try {
                String longLivedUrl = String.format(
                        "https://graph.facebook.com/v21.0/oauth/access_token?grant_type=fb_exchange_token&client_id=%s&client_secret=%s&fb_exchange_token=%s",
                        URLEncoder.encode(appId.trim(), StandardCharsets.UTF_8),
                        URLEncoder.encode(appSecret != null ? appSecret.trim() : "", StandardCharsets.UTF_8),
                        URLEncoder.encode(shortLivedToken, StandardCharsets.UTF_8)
                );

                HttpRequest longLivedReq = HttpRequest.newBuilder()
                        .uri(URI.create(longLivedUrl))
                        .header("Accept", "application/json")
                        .timeout(Duration.ofSeconds(20))
                        .GET()
                        .build();

                HttpResponse<String> longLivedRes = httpClient.send(longLivedReq, HttpResponse.BodyHandlers.ofString());
                JsonNode longLivedJson = objectMapper.readTree(longLivedRes.body());
                if (longLivedJson.has("access_token")) {
                    finalAccessToken = longLivedJson.path("access_token").asText();
                    expiresIn = longLivedJson.path("expires_in").asLong(5184000); // 60 days
                    log.info("Successfully acquired long-lived Instagram token (expires in {} seconds)", expiresIn);
                }
            } catch (Exception e) {
                log.warn("Could not upgrade to long-lived token, using short-lived token: {}", e.getMessage());
            }

            // 3. Save to database MarketplaceConfig
            String targetAccountId = (shopId != null && !shopId.isBlank()) ? shopId.trim() : defaultAccountId.trim();
            String targetAccountName = defaultAccountUsername.trim();

            saveOrUpdateConfig(targetAccountId, targetAccountName, finalAccessToken, null, expiresIn);
            log.info("Instagram account '{}' (ID: {}) successfully connected and saved to database", targetAccountName, targetAccountId);

        } catch (Exception e) {
            log.error("Error processing Instagram auth callback", e);
            throw new RuntimeException("Gagal memproses otorisasi Instagram: " + e.getMessage(), e);
        }
    }

    @Transactional
    public void saveOrUpdateConfig(String accountId, String accountUsername, String accessToken, String refreshToken, long expiresInSeconds) {
        MarketplaceConfig config = configRepository
                .findByMarketplaceTypeAndShopId("INSTAGRAM", accountId)
                .orElseGet(() -> MarketplaceConfig.builder()
                        .marketplaceType("INSTAGRAM")
                        .shopId(accountId)
                        .build());

        config.setShopName(accountUsername);
        config.setAccessToken(accessToken);
        if (refreshToken != null && !refreshToken.isBlank()) {
            config.setRefreshToken(refreshToken);
        }
        config.setTokenExpiry(LocalDateTime.now().plusSeconds(expiresInSeconds > 0 ? expiresInSeconds : 5184000));
        config.setIsActive(true);

        configRepository.save(config);
        log.info("MarketplaceConfig updated for INSTAGRAM shopId={}", accountId);
    }

    @Override
    public List<Map<String, Object>> getAuthorizedShops() {
        List<MarketplaceConfig> configs = configRepository.findAllByMarketplaceType("INSTAGRAM");
        List<Map<String, Object>> result = new ArrayList<>();

        for (MarketplaceConfig c : configs) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", c.getId());
            map.put("shopId", c.getShopId());
            map.put("shopName", c.getShopName() != null ? c.getShopName() : defaultAccountUsername);
            map.put("marketplaceType", "INSTAGRAM");
            map.put("isActive", Boolean.TRUE.equals(c.getIsActive()));
            map.put("hasToken", c.getAccessToken() != null && !c.getAccessToken().isBlank());
            map.put("tokenExpiry", c.getTokenExpiry());
            result.add(map);
        }

        // If no config saved yet in db, show default status
        if (result.isEmpty()) {
            Map<String, Object> defaultMap = new LinkedHashMap<>();
            defaultMap.put("shopId", defaultAccountId);
            defaultMap.put("shopName", defaultAccountUsername);
            defaultMap.put("marketplaceType", "INSTAGRAM");
            defaultMap.put("isActive", false);
            defaultMap.put("hasToken", false);
            result.add(defaultMap);
        }

        return result;
    }

    @Override
    @Transactional
    public void disconnectShop(String shopId) {
        log.info("Disconnecting Instagram account with shopId: {}", shopId);
        configRepository.findByMarketplaceTypeAndShopId("INSTAGRAM", shopId)
                .ifPresent(config -> {
                    config.setIsActive(false);
                    config.setAccessToken(null);
                    configRepository.save(config);
                    log.info("Instagram account {} disconnected", shopId);
                });
    }

    @Override
    public void pushProduct(Product product) {
        log.info("Request to publish product to Instagram feed: id={}, name={}", product.getId(), product.getName());
        // Can be used with Instagram Content Publishing API:
        // Step 1: POST /{ig-user-id}/media?image_url=...&caption=...
        // Step 2: POST /{ig-user-id}/media_publish?creation_id=...
    }

    @Override
    public void updateProduct(Product product) {
        log.info("Update product on Instagram (catalog/tagging): id={}", product.getId());
    }

    @Override
    public void updateStock(Product product, Integer newStock) {
        log.info("Stock update for Instagram product: id={}, newStock={}", product.getId(), newStock);
    }

    @Override
    public void deleteProduct(Product product) {
        log.info("Delete/hide product on Instagram: id={}", product.getId());
    }

    public boolean validateWebhookSignature(String signatureHeader, String rawPayload) {
        if (appSecret == null || appSecret.isBlank()) {
            log.warn("Instagram app secret is not configured; skipping signature verification");
            return true;
        }
        if (signatureHeader == null || !signatureHeader.startsWith("sha256=")) {
            log.warn("Missing or invalid X-Hub-Signature-256 header");
            return false;
        }

        try {
            String expectedSignature = signatureHeader.substring("sha256=".length()).trim();
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(appSecret.trim().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] hash = mac.doFinal(rawPayload != null ? rawPayload.getBytes(StandardCharsets.UTF_8) : new byte[0]);

            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }

            return hexString.toString().equalsIgnoreCase(expectedSignature);
        } catch (Exception e) {
            log.error("Error verifying Instagram webhook signature", e);
            return false;
        }
    }

    public void handleWebhookEvent(String rawPayload, String signatureHeader) {
        if (!validateWebhookSignature(signatureHeader, rawPayload)) {
            log.warn("Invalid signature for Instagram webhook event");
        }

        if (rawPayload == null || rawPayload.isBlank()) {
            return;
        }

        try {
            JsonNode root = objectMapper.readTree(rawPayload);
            String objectType = root.path("object").asText("");
            log.info("Processing Instagram webhook object: {}", objectType);

            JsonNode entries = root.path("entry");
            if (entries.isArray()) {
                for (JsonNode entry : entries) {
                    String accountId = entry.path("id").asText();
                    long time = entry.path("time").asLong();
                    log.info("Instagram entry for accountId={} at time={}", accountId, time);

                    // Handle changes (comments, mentions, live, etc.)
                    if (entry.has("changes")) {
                        for (JsonNode change : entry.path("changes")) {
                            String field = change.path("field").asText();
                            JsonNode value = change.path("value");
                            log.info("Instagram change event on field={}: value={}", field, value);
                        }
                    }

                    // Handle messaging (Direct Messages)
                    if (entry.has("messaging")) {
                        for (JsonNode message : entry.path("messaging")) {
                            log.info("Instagram direct message event: {}", message);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error parsing Instagram webhook payload", e);
        }
    }
}


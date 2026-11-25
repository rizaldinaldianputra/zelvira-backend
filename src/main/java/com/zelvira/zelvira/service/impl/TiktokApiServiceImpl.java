package com.zelvira.zelvira.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zelvira.zelvira.entity.MarketplaceConfig;
import com.zelvira.zelvira.entity.Product;
import com.zelvira.zelvira.repository.MarketplaceConfigRepository;
import com.zelvira.zelvira.repository.ProductImageRepository;
import com.zelvira.zelvira.service.MarketplaceService;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;

@Service
@Slf4j
public class TiktokApiServiceImpl implements MarketplaceService {

    @Value("${app.tiktok.app-key:}")
    private String appKey;

    @Value("${app.tiktok.app-secret:}")
    private String appSecret;

    @Value("${app.tiktok.auth-url:https://auth.tiktok-shops.com/oauth/authorize}")
    private String authUrl;

    @Value("${app.tiktok.api-base-url:https://open-api.tiktokglobalshop.com}")
    private String apiBaseUrl;

    @Value("${app.minio.url:http://minio:9000}")
    private String minioUrl;

    @Value("${app.minio.bucket:zelixa-bucket}")
    private String minioBucket;

    private final MarketplaceConfigRepository configRepository;
    private final ProductImageRepository productImageRepository;
    private final com.zelvira.zelvira.repository.ProductRepository productRepository;
    private final com.zelvira.zelvira.repository.ProductVariantRepository productVariantRepository;
    private final com.zelvira.zelvira.repository.ProductMarketplaceLinkRepository productMarketplaceLinkRepository;
    private final com.zelvira.zelvira.repository.MarketplaceOrderRepository marketplaceOrderRepository;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final MinioClient minioClient;
    private volatile String lastUploadErrorMessage = null;

    public TiktokApiServiceImpl(MarketplaceConfigRepository configRepository,
            ProductImageRepository productImageRepository,
            com.zelvira.zelvira.repository.ProductRepository productRepository,
            com.zelvira.zelvira.repository.ProductVariantRepository productVariantRepository,
            com.zelvira.zelvira.repository.ProductMarketplaceLinkRepository productMarketplaceLinkRepository,
            com.zelvira.zelvira.repository.MarketplaceOrderRepository marketplaceOrderRepository,
            ObjectMapper objectMapper,
            @org.springframework.beans.factory.annotation.Autowired(required = false) MinioClient minioClient) {
        this.configRepository = configRepository;
        this.productImageRepository = productImageRepository;
        this.productRepository = productRepository;
        this.productVariantRepository = productVariantRepository;
        this.productMarketplaceLinkRepository = productMarketplaceLinkRepository;
        this.marketplaceOrderRepository = marketplaceOrderRepository;
        this.objectMapper = objectMapper;
        this.minioClient = minioClient;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    @Override
    public String getMarketplaceType() {
        return "TIKTOK";
    }

    @Override
    public String getAuthUrl() {
        // Construct official TikTok Shop OAuth Authorization URL
        // Endpoint: https://auth.tiktok-shops.com/oauth/authorize?app_key=...
        String cleanAuthUrl = authUrl.trim();
        if (cleanAuthUrl.contains("services.tiktokshop.com/open/authorize")) {
            cleanAuthUrl = "https://auth.tiktok-shops.com/oauth/authorize";
        }
        if (!cleanAuthUrl.contains("app_key=")) {
            cleanAuthUrl += (cleanAuthUrl.contains("?") ? "&" : "?") + "app_key=" + appKey;
        }
        log.info("Generated TikTok Shop authorization URL: {}", cleanAuthUrl);
        return cleanAuthUrl;
    }

    @Override
    @Transactional
    public void processAuthCallback(String code, String shopId) {
        log.info("Processing TikTok Auth Callback with code: {} and shopId: {}", code, shopId);
        try {
            // 1. Exchange authorization code for access & refresh tokens
            // Endpoint: https://auth.tiktok-shops.com/api/v2/token/get
            String tokenUrl = String.format(
                    "https://auth.tiktok-shops.com/api/v2/token/get?app_key=%s&app_secret=%s&auth_code=%s&grant_type=authorized_code",
                    appKey, appSecret, code.trim());

            HttpRequest tokenRequest = HttpRequest.newBuilder()
                    .uri(URI.create(tokenUrl))
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(20))
                    .GET()
                    .build();

            HttpResponse<String> tokenResponse = httpClient.send(tokenRequest, HttpResponse.BodyHandlers.ofString());
            log.info("TikTok token exchange response code: {}, body: {}", tokenResponse.statusCode(),
                    tokenResponse.body());

            JsonNode tokenJson = objectMapper.readTree(tokenResponse.body());
            int codeStatus = tokenJson.path("code").asInt(-1);

            if (codeStatus != 0 && !tokenJson.has("data")) {
                String errMsg = tokenJson.path("message").asText("Failed to exchange TikTok authorization code");
                log.error("TikTok token exchange failed: {}", errMsg);
                throw new RuntimeException("TikTok Token Exchange Error: " + errMsg);
            }

            JsonNode data = tokenJson.path("data");
            String accessToken = data.path("access_token").asText();
            String refreshToken = data.path("refresh_token").asText();
            long expiresIn = data.path("access_token_expire_in").asLong(86400L);
            String sellerName = data.path("seller_name").asText("Zelvira TikTok Seller");
            String sellerRegion = data.path("seller_base_region").asText("ID");
            LocalDateTime expiry = LocalDateTime.now().plusSeconds(expiresIn);

            List<Map<String, String>> shops = new ArrayList<>();

            // A. Check if shop_list or shops is directly present in the token exchange
            // response
            JsonNode directShopsNode = data.has("shop_list") ? data.path("shop_list")
                    : (data.has("shops") ? data.path("shops") : null);

            if (directShopsNode != null && directShopsNode.isArray() && directShopsNode.size() > 0) {
                log.info("Found {} shops directly in TikTok token exchange response data", directShopsNode.size());
                for (JsonNode sn : directShopsNode) {
                    Map<String, String> s = new HashMap<>();
                    String sId = sn.has("shop_id") ? sn.path("shop_id").asText() : sn.path("id").asText();
                    String sName = sn.has("shop_name") ? sn.path("shop_name").asText()
                            : sn.path("name").asText(sellerName);
                    String sRegion = sn.has("region") ? sn.path("region").asText(sellerRegion) : sellerRegion;
                    String sCipher = sn.has("cipher") ? sn.path("cipher").asText()
                            : (sn.has("shop_cipher") ? sn.path("shop_cipher").asText() : "");
                    s.put("id", sId);
                    s.put("name", sName);
                    s.put("region", sRegion);
                    s.put("cipher", sCipher);
                    shops.add(s);
                }
            }

            // B. If not in token response, fetch from /authorization/202309/shops
            if (shops.isEmpty()) {
                shops = fetchRemoteShops(accessToken);
            }

            // C. If still empty, fetch from /authorization/202405/category_assets
            if (shops.isEmpty()) {
                shops = fetchCategoryAssets(accessToken);
            }

            // D. Check if cipher is directly on data object
            String directCipher = data.has("cipher") ? data.path("cipher").asText()
                    : (data.has("shop_cipher") ? data.path("shop_cipher").asText() : "");

            if (shops != null && !shops.isEmpty()) {
                for (Map<String, String> s : shops) {
                    String resolvedShopId = s.getOrDefault("id",
                            (shopId != null && !shopId.isBlank()) ? shopId : "TIKTOK_" + System.currentTimeMillis());
                    String shopName = s.getOrDefault("name", sellerName);
                    String region = s.getOrDefault("region", sellerRegion);
                    String cipher = s.getOrDefault("cipher", "");
                    if ((cipher == null || cipher.isBlank()) && directCipher != null && !directCipher.isBlank()) {
                        cipher = directCipher;
                    }

                    MarketplaceConfig config = configRepository.findByMarketplaceTypeAndShopId("TIKTOK", resolvedShopId)
                            .orElse(MarketplaceConfig.builder()
                                    .marketplaceType("TIKTOK")
                                    .shopId(resolvedShopId)
                                    .build());

                    config.setShopName(shopName);
                    config.setShopCipher(cipher);
                    config.setRegion(region);
                    config.setSellerName(sellerName);
                    config.setAccessToken(accessToken);
                    config.setRefreshToken(refreshToken);
                    config.setTokenExpiry(expiry);
                    config.setIsActive(true);

                    configRepository.save(config);
                    log.info("Saved TikTok Shop config for shopId: {}, name: {}, cipher: {}", resolvedShopId, shopName,
                            cipher);
                }
            } else {
                String resolvedShopId = (shopId != null && !shopId.isBlank()) ? shopId
                        : "TIKTOK_" + System.currentTimeMillis();
                MarketplaceConfig config = configRepository.findByMarketplaceTypeAndShopId("TIKTOK", resolvedShopId)
                        .orElse(MarketplaceConfig.builder()
                                .marketplaceType("TIKTOK")
                                .shopId(resolvedShopId)
                                .build());

                config.setShopName(sellerName);
                config.setShopCipher(directCipher);
                config.setRegion(sellerRegion);
                config.setSellerName(sellerName);
                config.setAccessToken(accessToken);
                config.setRefreshToken(refreshToken);
                config.setTokenExpiry(expiry);
                config.setIsActive(true);

                configRepository.save(config);
                log.info("Saved fallback TikTok Shop config for shopId: {}, seller: {}, cipher: {}", resolvedShopId,
                        sellerName, directCipher);
            }

        } catch (Exception e) {
            log.error("Exception during TikTok auth callback processing: {}", e.getMessage(), e);
            throw new RuntimeException("Gagal memproses autentikasi TikTok Shop: " + e.getMessage(), e);
        }
    }

    /**
     * Fetch list of authorized shops for the merchant from TikTok Open API.
     */
    private List<Map<String, String>> fetchRemoteShops(String accessToken) {
        List<Map<String, String>> result = new ArrayList<>();
        try {
            String path = "/authorization/202309/shops";
            long timestamp = Instant.now().getEpochSecond();

            Map<String, String> queryParams = new LinkedHashMap<>();
            queryParams.put("app_key", appKey);
            queryParams.put("timestamp", String.valueOf(timestamp));

            String sign = generateSignature(path, queryParams, "");
            String url = String.format("%s%s?app_key=%s&timestamp=%d&sign=%s", apiBaseUrl, path, appKey, timestamp,
                    sign);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Accept", "application/json")
                    .header("x-tts-access-token", accessToken)
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("TikTok get authorized shops response: {}", response.body());

            JsonNode root = objectMapper.readTree(response.body());
            if (root.path("code").asInt(0) == 0 && root.has("data")) {
                JsonNode dataNode = root.path("data");
                JsonNode shopsNode = dataNode.has("shops") ? dataNode.path("shops")
                        : (dataNode.has("shop_list") ? dataNode.path("shop_list") : null);

                if (shopsNode != null && shopsNode.isArray()) {
                    for (JsonNode sn : shopsNode) {
                        Map<String, String> shopData = new HashMap<>();
                        String sId = sn.has("shop_id") ? sn.path("shop_id").asText() : sn.path("id").asText();
                        String sName = sn.has("shop_name") ? sn.path("shop_name").asText() : sn.path("name").asText();
                        String sRegion = sn.has("region") ? sn.path("region").asText("ID") : "ID";
                        String sCipher = sn.has("cipher") ? sn.path("cipher").asText()
                                : (sn.has("shop_cipher") ? sn.path("shop_cipher").asText() : "");

                        shopData.put("id", sId);
                        shopData.put("name", sName);
                        shopData.put("region", sRegion);
                        shopData.put("cipher", sCipher);
                        result.add(shopData);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Could not query remote shops from TikTok API: {}", e.getMessage());
        }
        return result;
    }

    /**
     * Fetch authorized category assets (fallback for partner/TAP integrations).
     * Endpoint: GET /authorization/202405/category_assets
     */
    private List<Map<String, String>> fetchCategoryAssets(String accessToken) {
        List<Map<String, String>> result = new ArrayList<>();
        try {
            String path = "/authorization/202405/category_assets";
            long timestamp = Instant.now().getEpochSecond();

            Map<String, String> queryParams = new LinkedHashMap<>();
            queryParams.put("app_key", appKey);
            queryParams.put("timestamp", String.valueOf(timestamp));

            String sign = generateSignature(path, queryParams, "");
            String url = String.format("%s%s?app_key=%s&timestamp=%d&sign=%s", apiBaseUrl, path, appKey, timestamp,
                    sign);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Accept", "application/json")
                    .header("x-tts-access-token", accessToken)
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("TikTok get category assets response: {}", response.body());

            JsonNode root = objectMapper.readTree(response.body());
            if (root.path("code").asInt(0) == 0 && root.has("data")) {
                JsonNode assetsNode = root.path("data").path("category_assets");
                if (assetsNode.isArray()) {
                    for (JsonNode an : assetsNode) {
                        Map<String, String> assetData = new HashMap<>();
                        assetData.put("id", an.path("id").asText(""));
                        assetData.put("name", an.path("name").asText(""));
                        assetData.put("region", an.path("region").asText("ID"));
                        assetData.put("cipher", an.path("cipher").asText(""));
                        result.add(assetData);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Could not query category assets from TikTok API: {}", e.getMessage());
        }
        return result;
    }

    @Override
    public List<Map<String, Object>> getAuthorizedShops() {
        List<MarketplaceConfig> configs = configRepository.findAllByMarketplaceTypeAndIsActiveTrue("TIKTOK");
        List<Map<String, Object>> list = new ArrayList<>();

        for (MarketplaceConfig c : configs) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", c.getId());
            map.put("shopId", c.getShopId());
            map.put("shopName", c.getShopName() != null ? c.getShopName() : "TikTok Shop (" + c.getShopId() + ")");
            map.put("sellerName", c.getSellerName());
            map.put("region", c.getRegion() != null ? c.getRegion() : "ID");
            map.put("shopCipher", c.getShopCipher());
            map.put("isActive", c.getIsActive());
            map.put("tokenExpiry", c.getTokenExpiry());
            map.put("createdAt", c.getCreatedAt());
            map.put("updatedAt", c.getUpdatedAt());
            map.put("appKey", appKey);
            list.add(map);
        }

        return list;
    }

    @Override
    @Transactional
    public void disconnectShop(String shopId) {
        log.info("Disconnecting TikTok Shop with shopId: {}", shopId);
        Optional<MarketplaceConfig> configOpt = configRepository.findByMarketplaceTypeAndShopId("TIKTOK", shopId);
        if (configOpt.isPresent()) {
            MarketplaceConfig config = configOpt.get();
            config.setIsActive(false);
            config.setAccessToken(null);
            config.setRefreshToken(null);
            configRepository.save(config);
            log.info("TikTok Shop {} has been deactivated successfully", shopId);
        }
    }

    /**
     * Refreshes TikTok access token using refresh_token grant type.
     */
    @Transactional
    public synchronized boolean refreshAccessToken(MarketplaceConfig config) {
        if (config.getRefreshToken() == null || config.getRefreshToken().isBlank()) {
            log.warn("Cannot refresh TikTok token: refresh token is empty for shop {}", config.getShopId());
            return false;
        }

        try {
            String url = String.format(
                    "https://auth.tiktok-shops.com/api/v2/token/refresh?app_key=%s&app_secret=%s&grant_type=refresh_token&refresh_token=%s",
                    appKey, appSecret, config.getRefreshToken());

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("TikTok token refresh response: {}", response.body());

            JsonNode root = objectMapper.readTree(response.body());
            int code = root.path("code").asInt(-1);
            if (code == 0 && root.has("data")) {
                JsonNode data = root.path("data");
                String newAccessToken = data.path("access_token").asText();
                String newRefreshToken = data.path("refresh_token").asText(config.getRefreshToken());
                long expiresIn = data.path("access_token_expire_in").asLong(86400);

                config.setAccessToken(newAccessToken);
                config.setRefreshToken(newRefreshToken);
                config.setTokenExpiry(LocalDateTime.now().plusSeconds(expiresIn));

                // Check for shop_list or direct cipher in response
                JsonNode shopListNode = data.has("shop_list") ? data.path("shop_list")
                        : (data.has("shops") ? data.path("shops") : null);
                if (shopListNode != null && shopListNode.isArray() && shopListNode.size() > 0) {
                    for (JsonNode sn : shopListNode) {
                        String cipher = sn.has("cipher") ? sn.path("cipher").asText()
                                : (sn.has("shop_cipher") ? sn.path("shop_cipher").asText() : "");
                        if (cipher != null && !cipher.isBlank()) {
                            config.setShopCipher(cipher);
                            break;
                        }
                    }
                }

                configRepository.save(config);
                log.info("Successfully refreshed TikTok token and updated config for shop {}", config.getShopId());
                return true;
            } else {
                log.error("Failed to refresh TikTok token: {}", root.path("message").asText("Unknown error"));
                return false;
            }
        } catch (Exception e) {
            log.error("Exception refreshing TikTok token: {}", e.getMessage(), e);
            return false;
        }
    }

    public synchronized void ensureValidAccessToken(MarketplaceConfig config) {
        if (config == null) return;
        if (config.getRefreshToken() != null && !config.getRefreshToken().isBlank()) {
            LocalDateTime now = LocalDateTime.now();
            if (config.getTokenExpiry() == null || config.getTokenExpiry().isBefore(now.plusMinutes(15))) {
                log.info("TikTok access token for shop {} is expired or near expiry (expiry={}). Auto-refreshing token...",
                        config.getShopId(), config.getTokenExpiry());
                refreshAccessToken(config);
            }
        }
    }

    private boolean isTokenExpiredError(int code, String message) {
        if (code == 105001 || code == 105002 || code == 36000000 || code == 36000001) {
            return true;
        }
        if (message != null) {
            String lower = message.toLowerCase();
            return lower.contains("token is expired") || lower.contains("token expired")
                    || lower.contains("invalid access token") || lower.contains("access token invalid");
        }
        return false;
    }

    /**
     * Ensures a valid shop_cipher is present on the MarketplaceConfig.
     * If missing, attempts resolution via /authorization/202309/shops,
     * /authorization/202405/category_assets, and token refresh.
     */
    @Transactional
    public synchronized String ensureShopCipher(MarketplaceConfig config) {
        ensureValidAccessToken(config);
        if (config.getShopCipher() != null && !config.getShopCipher().isBlank()) {
            return config.getShopCipher();
        }

        log.info("Shop cipher is missing for shop {}. Attempting automatic resolution via TikTok APIs...",
                config.getShopId());
        String token = config.getAccessToken();

        // 1. Try fetchRemoteShops (/authorization/202309/shops)
        if (token != null && !token.isBlank()) {
            List<Map<String, String>> shops = fetchRemoteShops(token);
            for (Map<String, String> s : shops) {
                String c = s.get("cipher");
                if (c != null && !c.isBlank()) {
                    config.setShopCipher(c);
                    if (s.get("name") != null && !s.get("name").isBlank()) {
                        config.setShopName(s.get("name"));
                    }
                    configRepository.save(config);
                    log.info("Resolved shop cipher {} via fetchRemoteShops for shop {}", c, config.getShopId());
                    return c;
                }
            }

            // 2. Try fetchCategoryAssets (/authorization/202405/category_assets)
            List<Map<String, String>> assets = fetchCategoryAssets(token);
            for (Map<String, String> a : assets) {
                String c = a.get("cipher");
                if (c != null && !c.isBlank()) {
                    config.setShopCipher(c);
                    configRepository.save(config);
                    log.info("Resolved cipher {} via fetchCategoryAssets for shop {}", c, config.getShopId());
                    return c;
                }
            }
        }

        // 3. Try token refresh if refreshToken is available
        if (config.getRefreshToken() != null && !config.getRefreshToken().isBlank()) {
            log.info("Attempting token refresh to obtain cipher for shop {}", config.getShopId());
            boolean refreshed = refreshAccessToken(config);
            if (refreshed) {
                if (config.getShopCipher() != null && !config.getShopCipher().isBlank()) {
                    return config.getShopCipher();
                }
                // Retry fetchRemoteShops with the refreshed access token
                List<Map<String, String>> shops = fetchRemoteShops(config.getAccessToken());
                for (Map<String, String> s : shops) {
                    String c = s.get("cipher");
                    if (c != null && !c.isBlank()) {
                        config.setShopCipher(c);
                        if (s.get("name") != null && !s.get("name").isBlank()) {
                            config.setShopName(s.get("name"));
                        }
                        configRepository.save(config);
                        log.info("Resolved shop cipher {} after token refresh for shop {}", c, config.getShopId());
                        return c;
                    }
                }
            }
        }

        throw new IllegalStateException("Shop cipher belum tersedia untuk toko TikTok ini (" +
                (config.getShopName() != null ? config.getShopName() : config.getShopId()) +
                "). Silakan klik 'Sinkronkan Cipher Toko' atau sambungkan ulang toko TikTok di menu Integrasi.");
    }

    @Override
    @Transactional
    public Map<String, Object> syncShopCipher(String shopId) {
        log.info("Explicitly syncing shop cipher for TikTok shopId: {}", shopId);
        Optional<MarketplaceConfig> configOpt;
        if (shopId != null && !shopId.isBlank()) {
            configOpt = configRepository.findByMarketplaceTypeAndShopId("TIKTOK", shopId);
        } else {
            configOpt = configRepository.findFirstByMarketplaceTypeAndIsActiveTrue("TIKTOK");
        }

        if (configOpt.isEmpty()) {
            throw new IllegalStateException(
                    "Toko TikTok belum terhubung. Hubungkan toko terlebih dahulu di menu Integrasi.");
        }

        MarketplaceConfig config = configOpt.get();
        // Force re-fetch by clearing cipher temporarily in memory
        config.setShopCipher(null);
        String resolvedCipher = ensureShopCipher(config);

        return Map.of(
                "success", true,
                "shopId", config.getShopId(),
                "shopName", config.getShopName() != null ? config.getShopName() : config.getShopId(),
                "shopCipher", resolvedCipher,
                "message", "Shop cipher berhasil disinkronkan!");
    }

    /**
     * Resolves image bytes from MinIO, public HTTP, internal MinIO HTTP, or local
     * filesystem.
     */
    private byte[] fetchImageBytes(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return null;
        }

        // 1. Direct MinIO Client fetch if available
        if (minioClient != null) {
            try {
                String bucket = minioBucket;
                String objectName = null;

                if (!imageUrl.startsWith("http://") && !imageUrl.startsWith("https://")) {
                    String cleanPath = imageUrl.startsWith("/") ? imageUrl.substring(1) : imageUrl;
                    if (cleanPath.startsWith(minioBucket + "/")) {
                        objectName = cleanPath.substring(minioBucket.length() + 1);
                    } else if (cleanPath.contains("/")) {
                        int slashIdx = cleanPath.indexOf("/");
                        bucket = cleanPath.substring(0, slashIdx);
                        objectName = cleanPath.substring(slashIdx + 1);
                    } else {
                        objectName = cleanPath;
                    }
                } else if (imageUrl.contains(minioBucket) || imageUrl.contains("minio")
                        || imageUrl.contains("storage")) {
                    URI uri = URI.create(imageUrl);
                    String path = uri.getPath();
                    if (path != null) {
                        if (path.startsWith("/"))
                            path = path.substring(1);
                        if (path.startsWith(minioBucket + "/")) {
                            objectName = path.substring(minioBucket.length() + 1);
                        } else {
                            objectName = path;
                        }
                    }
                }

                if (objectName != null && !objectName.isBlank()) {
                    if (objectName.contains("?")) {
                        objectName = objectName.substring(0, objectName.indexOf("?"));
                    }
                    try (InputStream stream = minioClient.getObject(
                            GetObjectArgs.builder().bucket(bucket).object(objectName).build())) {
                        byte[] bytes = stream.readAllBytes();
                        if (bytes != null && bytes.length > 0) {
                            log.info("Successfully fetched {} image bytes directly from MinIO object: {}", bytes.length,
                                    objectName);
                            return bytes;
                        }
                    }
                }
            } catch (Exception e) {
                log.debug("Direct MinIO download failed for {}: {}", imageUrl, e.getMessage());
            }
        }

        // 2. HTTP/HTTPS download
        if (imageUrl.startsWith("http://") || imageUrl.startsWith("https://")) {
            String resolvedUrl = imageUrl;
            if (imageUrl.contains("zelixa-minio:9000")) {
                resolvedUrl = imageUrl.replace("http://zelixa-minio:9000",
                        minioUrl.endsWith("/") ? minioUrl.substring(0, minioUrl.length() - 1) : minioUrl);
            }
            try {
                HttpRequest downloadReq = HttpRequest.newBuilder()
                        .uri(URI.create(resolvedUrl))
                        .timeout(Duration.ofSeconds(15))
                        .GET()
                        .build();
                HttpResponse<byte[]> downloadResp = httpClient.send(downloadReq,
                        HttpResponse.BodyHandlers.ofByteArray());
                if (downloadResp.statusCode() == 200 && downloadResp.body() != null && downloadResp.body().length > 0) {
                    return downloadResp.body();
                }
            } catch (Exception e) {
                log.warn("Public HTTP download failed for {} (resolved: {}): {}", imageUrl, resolvedUrl,
                        e.getMessage());
            }

            // Fallback: If public domain failed (e.g. DNS loopback inside docker), try
            // internal minioUrl
            if (imageUrl.contains(minioBucket) || imageUrl.contains("minio") || imageUrl.contains("storage")) {
                try {
                    URI uri = URI.create(imageUrl);
                    String path = uri.getPath();
                    String internalUrl = (minioUrl.endsWith("/") ? minioUrl.substring(0, minioUrl.length() - 1)
                            : minioUrl) + path;
                    HttpRequest internalReq = HttpRequest.newBuilder()
                            .uri(URI.create(internalUrl))
                            .timeout(Duration.ofSeconds(10))
                            .GET()
                            .build();
                    HttpResponse<byte[]> internalResp = httpClient.send(internalReq,
                            HttpResponse.BodyHandlers.ofByteArray());
                    if (internalResp.statusCode() == 200 && internalResp.body() != null
                            && internalResp.body().length > 0) {
                        return internalResp.body();
                    }
                } catch (Exception e) {
                    log.debug("Internal MinIO HTTP fallback failed: {}", e.getMessage());
                }
            }
        }

        // 3. Relative path constructed with internal minioUrl
        if (!imageUrl.startsWith("http://") && !imageUrl.startsWith("https://")) {
            try {
                String cleanPath = imageUrl.startsWith("/") ? imageUrl : "/" + imageUrl;
                String internalHttp = (minioUrl.endsWith("/") ? minioUrl.substring(0, minioUrl.length() - 1) : minioUrl)
                        + cleanPath;
                HttpRequest internalReq = HttpRequest.newBuilder()
                        .uri(URI.create(internalHttp))
                        .timeout(Duration.ofSeconds(10))
                        .GET()
                        .build();
                HttpResponse<byte[]> internalResp = httpClient.send(internalReq,
                        HttpResponse.BodyHandlers.ofByteArray());
                if (internalResp.statusCode() == 200 && internalResp.body() != null && internalResp.body().length > 0) {
                    return internalResp.body();
                }
            } catch (Exception e) {
                log.debug("Relative MinIO HTTP download failed for {}: {}", imageUrl, e.getMessage());
            }

            // 4. Local file system check
            try {
                Path localPath = Paths.get(imageUrl);
                if (Files.exists(localPath)) {
                    return Files.readAllBytes(localPath);
                }
            } catch (Exception ignored) {
            }
        }

        return null;
    }

    /**
     * Uploads raw image bytes to TikTok Shop via POST
     * /product/202309/images/upload.
     */
    private String uploadProductImageBytes(byte[] imageBytes, String fileName, String useCase, String shopCipher,
            String accessToken) {
        if (imageBytes == null || imageBytes.length == 0) {
            return null;
        }
        try {
            String path = "/product/202309/images/upload";
            long timestamp = Instant.now().getEpochSecond();
            Map<String, String> queryParams = new LinkedHashMap<>();
            queryParams.put("app_key", appKey);
            if (shopCipher != null && !shopCipher.isBlank()) {
                queryParams.put("shop_cipher", shopCipher);
            }
            queryParams.put("timestamp", String.valueOf(timestamp));

            String sign = generateSignature(path, queryParams, "");
            String cipherParam = (shopCipher != null && !shopCipher.isBlank()) ? "&shop_cipher=" + shopCipher : "";
            String url = String.format("%s%s?app_key=%s%s&timestamp=%d&sign=%s",
                    apiBaseUrl, path, appKey, cipherParam, timestamp, sign);

            String actualUseCase = (useCase != null && !useCase.isBlank()) ? useCase : "MAIN_IMAGE";
            String boundary = "----WebKitFormBoundary" + Long.toHexString(System.currentTimeMillis());
            byte[] multipartBody = buildMultipartFormData(boundary, "data", fileName, "image/jpeg", imageBytes,
                    Map.of("use_case", actualUseCase));

            HttpRequest uploadReq = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .header("Accept", "application/json")
                    .header("x-tts-access-token", accessToken)
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofByteArray(multipartBody))
                    .build();

            HttpResponse<String> response = httpClient.send(uploadReq, HttpResponse.BodyHandlers.ofString());
            log.info("TikTok upload image response: {}", response.body());

            JsonNode root = objectMapper.readTree(response.body());
            int code = root.path("code").asInt(-1);
            if (code == 0 && root.has("data")) {
                String uri = root.path("data").path("uri").asText();
                if (uri != null && !uri.isBlank()) {
                    return uri;
                }
            } else {
                String errMsg = root.path("message").asText("Unknown error");
                log.warn("TikTok image upload returned non-zero code {}: {} (body={})", code, errMsg, response.body());
                lastUploadErrorMessage = "TikTok API (" + code + "): " + errMsg;
            }
        } catch (Exception e) {
            log.warn("Failed to upload image bytes to TikTok: {}", e.getMessage());
            lastUploadErrorMessage = "Koneksi upload TikTok gagal: " + e.getMessage();
        }
        return null;
    }

    /**
     * Generates a 1:1 high resolution (800x800) official branded product image
     * to fulfill TikTok Shop's mandatory MainImages requirement if product has no
     * photos.
     */
    private byte[] generateFallbackProductImage(String productName) {
        try {
            int width = 800;
            int height = 800;
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2d = image.createGraphics();

            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            // Elegant dark background
            GradientPaint gradient = new GradientPaint(0, 0, new Color(15, 23, 42), width, height,
                    new Color(30, 41, 59));
            g2d.setPaint(gradient);
            g2d.fillRect(0, 0, width, height);

            // Card container
            g2d.setColor(new Color(255, 255, 255, 18));
            g2d.fillRoundRect(60, 60, width - 120, height - 120, 36, 36);

            // Pink accent frame
            g2d.setColor(new Color(236, 72, 153));
            g2d.setStroke(new BasicStroke(4));
            g2d.drawRoundRect(60, 60, width - 120, height - 120, 36, 36);

            // Top Brand Badge
            g2d.setFont(new Font("SansSerif", Font.BOLD, 32));
            FontMetrics fm = g2d.getFontMetrics();
            String brand = "ZELVIRA OFFICIAL STORE";
            g2d.drawString(brand, (width - fm.stringWidth(brand)) / 2, 220);

            // Product Name
            g2d.setColor(Color.WHITE);
            g2d.setFont(new Font("SansSerif", Font.BOLD, 42));
            fm = g2d.getFontMetrics();
            String title = (productName != null && !productName.isBlank()) ? productName : "Zelvira Collection";
            if (title.length() > 22) {
                title = title.substring(0, 20) + "...";
            }
            g2d.drawString(title, (width - fm.stringWidth(title)) / 2, 420);

            // Quality Guarantee Subtitle
            g2d.setColor(new Color(148, 163, 184));
            g2d.setFont(new Font("SansSerif", Font.PLAIN, 24));
            fm = g2d.getFontMetrics();
            String subtitle = "Original & Guaranteed Quality Product";
            g2d.drawString(subtitle, (width - fm.stringWidth(subtitle)) / 2, 480);

            // Bottom pill
            g2d.setColor(new Color(236, 72, 153, 50));
            g2d.fillRoundRect(220, 560, 360, 50, 25, 25);
            g2d.setColor(new Color(244, 114, 182));
            g2d.setFont(new Font("SansSerif", Font.BOLD, 20));
            fm = g2d.getFontMetrics();
            String badgeText = "100% AUTHENTIC";
            g2d.drawString(badgeText, (width - fm.stringWidth(badgeText)) / 2, 592);

            g2d.dispose();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "jpg", baos);
            return baos.toByteArray();
        } catch (Exception e) {
            log.warn("Failed to generate fallback product image: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Generates a high-resolution (1200x1200) standard size chart image
     * to fulfill TikTok Shop's mandatory size_chart requirement for fashion
     * products.
     */
    private byte[] generateSizeChartImage(String productName) {
        try {
            int width = 1200;
            int height = 1200;
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2d = image.createGraphics();

            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            // Dark background
            g2d.setColor(new Color(15, 23, 42)); // slate-900
            g2d.fillRect(0, 0, width, height);

            // Outer decorative frame
            g2d.setColor(new Color(236, 72, 153)); // pink-500
            g2d.setStroke(new BasicStroke(4));
            g2d.drawRoundRect(50, 50, width - 100, height - 100, 30, 30);

            // Header Container
            g2d.setColor(new Color(30, 41, 59)); // slate-800
            g2d.fillRoundRect(80, 80, width - 160, 160, 24, 24);

            // Brand & Title
            g2d.setColor(new Color(244, 114, 182)); // pink-400
            g2d.setFont(new Font("SansSerif", Font.BOLD, 26));
            FontMetrics fm = g2d.getFontMetrics();
            String brandText = "ZELVIRA OFFICIAL STORE";
            g2d.drawString(brandText, (width - fm.stringWidth(brandText)) / 2, 130);

            g2d.setColor(Color.WHITE);
            g2d.setFont(new Font("SansSerif", Font.BOLD, 40));
            fm = g2d.getFontMetrics();
            String titleText = "PANDUAN UKURAN / SIZE CHART";
            g2d.drawString(titleText, (width - fm.stringWidth(titleText)) / 2, 185);

            String subTitle = (productName != null && !productName.isBlank()) ? productName
                    : "Apparel & Fashion Collection";
            if (subTitle.length() > 38) {
                subTitle = subTitle.substring(0, 35) + "...";
            }
            g2d.setColor(new Color(148, 163, 184)); // slate-400
            g2d.setFont(new Font("SansSerif", Font.PLAIN, 22));
            fm = g2d.getFontMetrics();
            g2d.drawString(subTitle, (width - fm.stringWidth(subTitle)) / 2, 222);

            // Table Header
            int tableTop = 270;
            int rowHeight = 75;
            int colX0 = 80;
            int tableWidth = width - 160;

            g2d.setColor(new Color(236, 72, 153)); // pink-500
            g2d.fillRoundRect(colX0, tableTop, tableWidth, 65, 16, 16);

            g2d.setColor(Color.WHITE);
            g2d.setFont(new Font("SansSerif", Font.BOLD, 22));
            g2d.drawString("UKURAN", colX0 + 40, tableTop + 42);
            g2d.drawString("LEBAR DADA", colX0 + 260, tableTop + 42);
            g2d.drawString("PANJANG", colX0 + 540, tableTop + 42);
            g2d.drawString("PANJANG LENGAN", colX0 + 740, tableTop + 42);

            // Table Rows
            String[][] rows = new String[][] {
                    { "S", "48 cm", "68 cm", "21 cm" },
                    { "M", "50 cm", "70 cm", "22 cm" },
                    { "L", "52 cm", "72 cm", "23 cm" },
                    { "XL", "54 cm", "74 cm", "24 cm" },
                    { "XXL", "56 cm", "76 cm", "25 cm" },
                    { "All Size", "52 cm", "72 cm", "23 cm" }
            };

            int currY = tableTop + 75;
            for (int i = 0; i < rows.length; i++) {
                if (i % 2 == 0) {
                    g2d.setColor(new Color(30, 41, 59, 180));
                } else {
                    g2d.setColor(new Color(15, 23, 42, 180));
                }
                g2d.fillRoundRect(colX0, currY, tableWidth, rowHeight - 8, 12, 12);

                g2d.setColor(new Color(244, 114, 182));
                g2d.setFont(new Font("SansSerif", Font.BOLD, 24));
                g2d.drawString(rows[i][0], colX0 + 50, currY + 45);

                g2d.setColor(Color.WHITE);
                g2d.setFont(new Font("SansSerif", Font.PLAIN, 22));
                g2d.drawString(rows[i][1], colX0 + 280, currY + 45);
                g2d.drawString(rows[i][2], colX0 + 550, currY + 45);
                g2d.drawString(rows[i][3], colX0 + 780, currY + 45);

                currY += rowHeight;
            }

            // Footer Tips & Notes
            int footerTop = currY + 20;
            g2d.setColor(new Color(30, 41, 59));
            g2d.fillRoundRect(colX0, footerTop, tableWidth, 140, 20, 20);

            g2d.setColor(new Color(244, 114, 182));
            g2d.setFont(new Font("SansSerif", Font.BOLD, 20));
            g2d.drawString("CATATAN UKURAN:", colX0 + 30, footerTop + 40);

            g2d.setColor(new Color(203, 213, 225));
            g2d.setFont(new Font("SansSerif", Font.PLAIN, 18));
            g2d.drawString("1. Toleransi ukuran 1 - 2 cm karena pengukuran manual saat proses produksi.", colX0 + 30,
                    footerTop + 72);
            g2d.drawString("2. Pilih 1 ukuran lebih besar jika Anda menyukai gaya santai / oversized fit.", colX0 + 30,
                    footerTop + 102);

            g2d.setColor(new Color(100, 116, 139));
            g2d.setFont(new Font("SansSerif", Font.BOLD, 18));
            fm = g2d.getFontMetrics();
            String copyText = "© Zelvira Official • Jaminan Produk Original 100%";
            g2d.drawString(copyText, (width - fm.stringWidth(copyText)) / 2, height - 70);

            g2d.dispose();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "jpg", baos);
            return baos.toByteArray();
        } catch (Exception e) {
            log.warn("Failed to generate size chart image: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Uploads an image to TikTok Shop via POST /product/202309/images/upload
     * to obtain the required internal image URI (e.g. tos-alisg-...).
     */
    private String uploadProductImage(String imageUrl, String shopCipher, String accessToken) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return null;
        }
        if (imageUrl.startsWith("tos-")) {
            return imageUrl;
        }
        byte[] imageBytes = fetchImageBytes(imageUrl);
        if (imageBytes == null || imageBytes.length == 0) {
            log.warn("Could not retrieve image bytes for URL: {}", imageUrl);
            return null;
        }
        return uploadProductImageBytes(imageBytes, "product.jpg", "MAIN_IMAGE", shopCipher, accessToken);
    }

    private byte[] buildMultipartFormData(String boundary, String fileParamName, String fileName, String mimeType,
            byte[] fileBytes, Map<String, String> textFields) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        String lineBreak = "\r\n";

        if (textFields != null) {
            for (Map.Entry<String, String> entry : textFields.entrySet()) {
                baos.write(("--" + boundary + lineBreak).getBytes(StandardCharsets.UTF_8));
                baos.write(("Content-Disposition: form-data; name=\"" + entry.getKey() + "\"" + lineBreak + lineBreak)
                        .getBytes(StandardCharsets.UTF_8));
                baos.write((entry.getValue() + lineBreak).getBytes(StandardCharsets.UTF_8));
            }
        }

        baos.write(("--" + boundary + lineBreak).getBytes(StandardCharsets.UTF_8));
        baos.write(("Content-Disposition: form-data; name=\"" + fileParamName + "\"; filename=\"" + fileName + "\""
                + lineBreak).getBytes(StandardCharsets.UTF_8));
        baos.write(("Content-Type: " + mimeType + lineBreak + lineBreak).getBytes(StandardCharsets.UTF_8));
        baos.write(fileBytes);
        baos.write(lineBreak.getBytes(StandardCharsets.UTF_8));

        baos.write(("--" + boundary + "--" + lineBreak).getBytes(StandardCharsets.UTF_8));
        return baos.toByteArray();
    }

    /**
     * Retrieves the default warehouse ID for the shop.
     * Endpoint: GET /logistics/202309/warehouses
     */
    public synchronized String getOrFetchDefaultWarehouseId(MarketplaceConfig config) {
        try {
            String cipher = ensureShopCipher(config);
            String path = "/logistics/202309/warehouses";
            long timestamp = Instant.now().getEpochSecond();

            Map<String, String> queryParams = new LinkedHashMap<>();
            queryParams.put("app_key", appKey);
            queryParams.put("shop_cipher", cipher);
            queryParams.put("timestamp", String.valueOf(timestamp));

            String sign = generateSignature(path, queryParams, "");
            String url = String.format("%s%s?app_key=%s&shop_cipher=%s&timestamp=%d&sign=%s",
                    apiBaseUrl, path, appKey, cipher, timestamp, sign);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("x-tts-access-token", config.getAccessToken())
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("TikTok get warehouses response: {}", response.body());

            JsonNode root = objectMapper.readTree(response.body());
            if (root.path("code").asInt(-1) == 0 && root.has("data")) {
                JsonNode warehouses = root.path("data").path("warehouses");
                if (warehouses.isArray() && warehouses.size() > 0) {
                    for (JsonNode wh : warehouses) {
                        if (wh.path("is_default").asBoolean(false)) {
                            return wh.path("id").asText();
                        }
                    }
                    return warehouses.get(0).path("id").asText();
                }
            }
        } catch (Exception e) {
            log.warn("Could not query warehouse list from TikTok API: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Resolves a valid V2 leaf category ID for the product on TikTok Shop.
     * Tries AI Category Recommendation endpoint first, then falls back to V2
     * Category Tree.
     */
    private String resolveV2LeafCategoryId(String title, String desc, List<Map<String, String>> mainImages,
            MarketplaceConfig config) {
        String cipher = ensureShopCipher(config);

        // 1. Try Category Recommendation API (POST
        // /product/202309/categories/recommend)
        try {
            String path = "/product/202309/categories/recommend";
            long timestamp = Instant.now().getEpochSecond();
            Map<String, String> queryParams = new LinkedHashMap<>();
            queryParams.put("app_key", appKey);
            queryParams.put("shop_cipher", cipher);
            queryParams.put("timestamp", String.valueOf(timestamp));

            Map<String, Object> body = new HashMap<>();
            body.put("product_title", title);
            if (desc != null && !desc.isBlank()) {
                body.put("description", desc);
            }
            if (mainImages != null && !mainImages.isEmpty()) {
                body.put("images", mainImages);
            }
            body.put("category_version", "v2");

            String bodyJson = objectMapper.writeValueAsString(body);
            String sign = generateSignature(path, queryParams, bodyJson);
            String url = String.format("%s%s?app_key=%s&shop_cipher=%s&timestamp=%d&sign=%s",
                    apiBaseUrl, path, appKey, cipher, timestamp, sign);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("x-tts-access-token", config.getAccessToken())
                    .timeout(Duration.ofSeconds(15))
                    .POST(HttpRequest.BodyPublishers.ofString(bodyJson))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("TikTok category recommend response: {}", response.body());

            JsonNode root = objectMapper.readTree(response.body());
            if (root.path("code").asInt(-1) == 0 && root.has("data")) {
                JsonNode data = root.path("data");
                if (data.has("leaf_category_id") && !data.path("leaf_category_id").asText().isBlank()) {
                    String leafId = data.path("leaf_category_id").asText();
                    log.info("Resolved recommended V2 leaf_category_id: {}", leafId);
                    return leafId;
                }
                if (data.has("categories") && data.path("categories").isArray() && data.path("categories").size() > 0) {
                    JsonNode catArray = data.path("categories");
                    JsonNode deepest = catArray.get(catArray.size() - 1);
                    if (deepest.has("id") && !deepest.path("id").asText().isBlank()) {
                        String leafId = deepest.path("id").asText();
                        log.info("Resolved V2 leaf_category_id from categories array: {}", leafId);
                        return leafId;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to get recommended category from TikTok: {}", e.getMessage());
        }

        // 2. Fallback: Query V2 Category Tree (GET /product/202309/categories)
        try {
            String path = "/product/202309/categories";
            long timestamp = Instant.now().getEpochSecond();
            Map<String, String> queryParams = new LinkedHashMap<>();
            queryParams.put("app_key", appKey);
            queryParams.put("category_version", "v2");
            queryParams.put("shop_cipher", cipher);
            queryParams.put("timestamp", String.valueOf(timestamp));

            String sign = generateSignature(path, queryParams, "");
            String url = String.format("%s%s?app_key=%s&category_version=v2&shop_cipher=%s&timestamp=%d&sign=%s",
                    apiBaseUrl, path, appKey, cipher, timestamp, sign);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("x-tts-access-token", config.getAccessToken())
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String respBody = response.body();
            log.info("TikTok get categories response (v2): {}",
                    respBody.length() > 300 ? respBody.substring(0, 300) + "..." : respBody);

            JsonNode root = objectMapper.readTree(respBody);
            if (root.path("code").asInt(-1) == 0 && root.has("data")) {
                JsonNode categoriesNode = root.path("data").path("categories");
                if (categoriesNode.isArray() && categoriesNode.size() > 0) {
                    String leafId = findFirstLeafCategoryId(categoriesNode);
                    if (leafId != null && !leafId.isBlank()) {
                        log.info("Found fallback V2 leaf category: {}", leafId);
                        return leafId;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to query V2 category list from TikTok: {}", e.getMessage());
        }

        // 3. Fallback to standard TikTok Shop V2 fashion leaf category
        return "601156";
    }

    private String findFirstLeafCategoryId(JsonNode node) {
        if (node.isArray()) {
            for (JsonNode child : node) {
                String found = findFirstLeafCategoryId(child);
                if (found != null)
                    return found;
            }
        } else if (node.isObject()) {
            if (node.path("is_leaf").asBoolean(false) && node.has("id")) {
                return node.path("id").asText();
            }
            if (node.has("children")) {
                return findFirstLeafCategoryId(node.path("children"));
            }
        }
        return null;
    }

    @Override
    public void pushProduct(Product product) {
        log.info("Step 2: Preparing to push product '{}' (ID: {}) to TikTok Shop", product.getName(), product.getId());

        // If product already has an active link with a remoteProductId, update it
        // instead of creating duplicate
        Optional<com.zelvira.zelvira.entity.ProductMarketplaceLink> existingLinkOpt = productMarketplaceLinkRepository
                .findByProductIdAndMarketplaceType(product.getId(), "TIKTOK");
        if (existingLinkOpt.isPresent() && existingLinkOpt.get().getMarketplaceProductId() != null
                && !existingLinkOpt.get().getMarketplaceProductId().isBlank()) {
            String remoteId = existingLinkOpt.get().getMarketplaceProductId().trim();
            log.info(
                    "Product '{}' already linked to TikTok Shop (ID: {}). Performing update sync instead of re-creating...",
                    product.getName(), remoteId);
            try {
                updateProduct(product);
                return;
            } catch (Exception e) {
                log.warn("Update existing TikTok product {} failed ({}), clearing stale link and attempting full re-push...",
                        remoteId, e.getMessage());
                try {
                    productMarketplaceLinkRepository.delete(existingLinkOpt.get());
                } catch (Exception delEx) {
                    log.warn("Could not delete stale link: {}", delEx.getMessage());
                }
            }
        }

        Optional<MarketplaceConfig> configOpt = configRepository.findFirstByMarketplaceTypeAndIsActiveTrue("TIKTOK");

        if (configOpt.isEmpty()) {
            throw new IllegalStateException(
                    "Toko TikTok belum terhubung. Silakan hubungkan toko Anda terlebih dahulu melalui menu Integrasi.");
        }

        MarketplaceConfig config = configOpt.get();
        ensureValidAccessToken(config);
        String cipher = ensureShopCipher(config);

        try {
            // Build TikTok Shop Create Product payload schema
            // Reference: /product/202309/products
            Map<String, Object> payload = new LinkedHashMap<>();

            // Title conforming to TikTok requirement (25-255 characters recommended)
            String title = product.getName() != null ? product.getName().trim() : "Produk Zelvira";
            if (title.length() < 25) {
                title = "Zelvira - " + title + " Original Collection";
            }
            if (title.length() > 255) {
                title = title.substring(0, 255);
            }
            payload.put("title", title);

            String desc = product.getDescription();
            if (desc == null || desc.isBlank() || desc.trim().length() < 20) {
                desc = "<p>" + title + " - Produk original berkualitas dari Zelvira Official Store.</p>";
            }
            payload.put("description", desc);

            // Image mapping - resolve candidate images from thumbnail, gallery, variants
            List<Map<String, String>> mainImages = new ArrayList<>();
            List<String> candidateImageUrls = new ArrayList<>();

            // 1. Master product thumbnail
            if (product.getThumbnailUrl() != null && !product.getThumbnailUrl().isBlank()) {
                candidateImageUrls.add(product.getThumbnailUrl().trim());
            }

            // 2. Product gallery images
            List<com.zelvira.zelvira.entity.ProductImage> images = productImageRepository
                    .findByProductIdOrderBySortOrderAsc(product.getId());
            if (images != null && !images.isEmpty()) {
                for (com.zelvira.zelvira.entity.ProductImage img : images) {
                    if (img.getUrl() != null && !img.getUrl().isBlank() && !candidateImageUrls.contains(img.getUrl().trim())) {
                        candidateImageUrls.add(img.getUrl().trim());
                    }
                }
            }

            // 3. Variant images if available
            if (product.getVariants() != null) {
                for (com.zelvira.zelvira.entity.ProductVariant v : product.getVariants()) {
                    if (v.getImageUrl() != null && !v.getImageUrl().isBlank() && !candidateImageUrls.contains(v.getImageUrl().trim())) {
                        candidateImageUrls.add(v.getImageUrl().trim());
                    }
                }
            }

            for (String imgUrl : candidateImageUrls) {
                if (imgUrl.startsWith("tos-")) {
                    mainImages.add(Map.of("uri", imgUrl));
                } else {
                    String imageUri = uploadProductImage(imgUrl, cipher, config.getAccessToken());
                    if (imageUri != null && !imageUri.isBlank()) {
                        mainImages.add(Map.of("uri", imageUri));
                    }
                }
                if (mainImages.size() >= 9) break;
            }

            // Fallback: If no reachable images, generate official 800x800 branded image
            if (mainImages.isEmpty()) {
                log.info(
                        "Product '{}' has no reachable images, generating official 800x800 fallback image for TikTok Shop...",
                        product.getName());
                byte[] fallbackBytes = generateFallbackProductImage(product.getName());
                if (fallbackBytes != null) {
                    String fallbackUri = uploadProductImageBytes(fallbackBytes, "main-image.jpg", "MAIN_IMAGE",
                            cipher, config.getAccessToken());
                    if (fallbackUri != null && !fallbackUri.isBlank()) {
                        mainImages.add(Map.of("uri", fallbackUri));
                    }
                }
            }

            if (mainImages.isEmpty()) {
                String detail = lastUploadErrorMessage != null ? lastUploadErrorMessage
                        : "Pastikan server dapat terhubung ke TikTok Shop dan token otorisasi masih aktif.";
                throw new IllegalStateException("Gagal menyiapkan MainImages untuk produk '" + product.getName()
                        + "': " + detail);
            }
            payload.put("main_images", mainImages);

            // Resolve valid V2 leaf category for TikTok Shop (Required for all-region
            // shops: error 12052217)
            String leafCatId = resolveV2LeafCategoryId(title, desc, mainImages, config);
            payload.put("category_id", leafCatId);
            payload.put("category_version", "v2");

            // Size chart mapping (Mandatory for fashion/apparel: error 12052673)
            try {
                byte[] sizeChartBytes = generateSizeChartImage(product.getName());
                if (sizeChartBytes != null) {
                    String sizeChartUri = uploadProductImageBytes(sizeChartBytes, "size-chart.jpg", "SIZE_CHART_IMAGE",
                            cipher, config.getAccessToken());
                    if (sizeChartUri != null && !sizeChartUri.isBlank()) {
                        payload.put("size_chart", Map.of(
                                "image", Map.of("uri", sizeChartUri)));
                        log.info("Attached size chart image with URI: {}", sizeChartUri);
                    }
                }
            } catch (Exception e) {
                log.warn("Could not generate or attach size chart for product {}: {}", product.getName(),
                        e.getMessage());
            }

            // Package dimensions & weight (weight must be a struct with value and unit)
            double weightKg = (product.getWeight() != null && product.getWeight() > 0)
                    ? Math.max(product.getWeight() / 1000.0, 0.1)
                    : 0.50;
            payload.put("package_weight", Map.of(
                    "value", String.format(Locale.US, "%.2f", weightKg),
                    "unit", "KILOGRAM"));
            payload.put("package_dimensions", Map.of(
                    "length", "20",
                    "width", "15",
                    "height", "5",
                    "unit", "CENTIMETER"));

            // Resolve default warehouse for inventory mapping
            String defaultWarehouseId = getOrFetchDefaultWarehouseId(config);

            // Price & SKU mapping from variants
            List<Map<String, Object>> skus = new ArrayList<>();
            String masterSku = product.getSku() != null && !product.getSku().isBlank()
                    ? product.getSku().trim()
                    : "SKU-" + product.getId();

            List<com.zelvira.zelvira.entity.ProductVariant> activeVariants = product.getVariants() != null
                    ? product.getVariants().stream()
                            .filter(v -> v.getIsActive() == null || v.getIsActive())
                            .toList()
                    : Collections.emptyList();

            if (activeVariants.isEmpty()) {
                activeVariants = productVariantRepository.findByProductId(product.getId()).stream()
                        .filter(v -> v.getIsActive() == null || v.getIsActive())
                        .toList();
            }

            boolean hasMultipleVariants = activeVariants.size() > 1;
            boolean anyHasColor = activeVariants.stream().anyMatch(v -> v.getColor() != null && !v.getColor().isBlank());
            boolean anyHasSize = activeVariants.stream().anyMatch(v -> v.getSize() != null && !v.getSize().isBlank());

            if (!activeVariants.isEmpty()) {
                for (int i = 0; i < activeVariants.size(); i++) {
                    com.zelvira.zelvira.entity.ProductVariant v = activeVariants.get(i);
                    Map<String, Object> skuMap = new LinkedHashMap<>();
                    String vSku = v.getSku() != null && !v.getSku().isBlank() ? v.getSku().trim()
                            : (masterSku + "-" + (i + 1));
                    skuMap.put("seller_sku", vSku);
                    skuMap.put("price", Map.of(
                            "amount", v.getPrice() != null ? v.getPrice().toPlainString() : "100000",
                            "currency", "IDR"));
                    Map<String, Object> invItem = new HashMap<>();
                    invItem.put("quantity", v.getStock() != null ? v.getStock() : 10);
                    if (defaultWarehouseId != null && !defaultWarehouseId.isBlank()) {
                        invItem.put("warehouse_id", defaultWarehouseId);
                    }
                    skuMap.put("inventory", List.of(invItem));

                    if (hasMultipleVariants) {
                        List<Map<String, Object>> salesAttrs = new ArrayList<>();
                        if (anyHasColor) {
                            String c = (v.getColor() != null && !v.getColor().isBlank()) ? v.getColor().trim() : ("Varian " + (i + 1));
                            salesAttrs.add(Map.of("attribute_name", "Color", "custom_value", c));
                        }
                        if (anyHasSize) {
                            String s = (v.getSize() != null && !v.getSize().isBlank()) ? v.getSize().trim() : "All Size";
                            salesAttrs.add(Map.of("attribute_name", "Size", "custom_value", s));
                        }
                        if (!anyHasColor && !anyHasSize) {
                            String customName = (v.getGroupName() != null && !v.getGroupName().isBlank())
                                    ? v.getGroupName().trim()
                                    : ("Pilihan " + (i + 1));
                            salesAttrs.add(Map.of("attribute_name", "Variation", "custom_value", customName));
                        }
                        skuMap.put("sales_attributes", salesAttrs);
                    }

                    skus.add(skuMap);
                }
            } else {
                Map<String, Object> defaultSku = new LinkedHashMap<>();
                defaultSku.put("seller_sku", masterSku);
                String priceStr = "100000";
                List<com.zelvira.zelvira.entity.ProductVariant> dbVars = productVariantRepository
                        .findByProductId(product.getId());
                if (!dbVars.isEmpty() && dbVars.get(0).getPrice() != null) {
                    priceStr = dbVars.get(0).getPrice().toPlainString();
                }
                defaultSku.put("price", Map.of(
                        "amount", priceStr,
                        "currency", "IDR"));
                Map<String, Object> invItem = new HashMap<>();
                invItem.put("quantity", 10);
                if (defaultWarehouseId != null && !defaultWarehouseId.isBlank()) {
                    invItem.put("warehouse_id", defaultWarehouseId);
                }
                defaultSku.put("inventory", List.of(invItem));
                skus.add(defaultSku);
            }
            payload.put("skus", skus);

            String bodyJson = objectMapper.writeValueAsString(payload);
            log.info("Constructed TikTok product payload: {}", bodyJson);

            String path = "/product/202309/products";
            long timestamp = Instant.now().getEpochSecond();
            Map<String, String> queryParams = new LinkedHashMap<>();
            queryParams.put("app_key", appKey);
            queryParams.put("shop_cipher", cipher);
            queryParams.put("timestamp", String.valueOf(timestamp));

            String sign = generateSignature(path, queryParams, bodyJson);
            String url = String.format("%s%s?app_key=%s&shop_cipher=%s&timestamp=%d&sign=%s",
                    apiBaseUrl, path, appKey, cipher, timestamp, sign);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("x-tts-access-token", config.getAccessToken())
                    .timeout(Duration.ofSeconds(20))
                    .POST(HttpRequest.BodyPublishers.ofString(bodyJson))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("TikTok push product response: {}", response.body());

            JsonNode root = objectMapper.readTree(response.body());
            int code = root.path("code").asInt(-1);

            // If token expired, auto-refresh and retry once
            if (isTokenExpiredError(code, root.path("message").asText()) && refreshAccessToken(config)) {
                log.info("Access token refreshed after token error. Retrying pushProduct for '{}'...", product.getName());
                timestamp = Instant.now().getEpochSecond();
                queryParams.put("timestamp", String.valueOf(timestamp));
                sign = generateSignature(path, queryParams, bodyJson);
                url = String.format("%s%s?app_key=%s&shop_cipher=%s&timestamp=%d&sign=%s",
                        apiBaseUrl, path, appKey, cipher, timestamp, sign);
                request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Content-Type", "application/json")
                        .header("Accept", "application/json")
                        .header("x-tts-access-token", config.getAccessToken())
                        .timeout(Duration.ofSeconds(20))
                        .POST(HttpRequest.BodyPublishers.ofString(bodyJson))
                        .build();
                response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                log.info("TikTok push product retry response: {}", response.body());
                root = objectMapper.readTree(response.body());
                code = root.path("code").asInt(-1);
            }

            if (code != 0) {
                String msg = root.path("message").asText("Gagal menambahkan produk ke TikTok Shop");
                log.error("TikTok push product error: code={}, message={}, body={}", code, msg, response.body());
                throw new RuntimeException("TikTok API Error (" + code + "): " + msg);
            }

            String remoteProductId = root.path("data").path("product_id").asText("");
            log.info("Product '{}' successfully pushed to TikTok Shop with ID: {}", product.getName(), remoteProductId);

            if (remoteProductId != null && !remoteProductId.isBlank()) {
                com.zelvira.zelvira.entity.ProductMarketplaceLink link = productMarketplaceLinkRepository
                        .findByProductIdAndMarketplaceType(product.getId(), "TIKTOK")
                        .orElse(com.zelvira.zelvira.entity.ProductMarketplaceLink.builder()
                                .product(product)
                                .marketplaceType("TIKTOK")
                                .build());
                link.setMarketplaceProductId(remoteProductId);
                link.setStatus("SYNCED");
                link.setLastSyncTime(LocalDateTime.now());
                link.setErrorMessage(null);
                productMarketplaceLinkRepository.save(link);
            }

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to push product to TikTok: {}", e.getMessage(), e);
            throw new RuntimeException("Gagal push produk ke TikTok Shop: " + e.getMessage(), e);
        }
    }

    @Override
    public void updateProduct(Product product) {
        if (product == null || product.getId() == null)
            return;

        log.info("Synchronizing updated product '{}' (ID: {}) to TikTok Shop...", product.getName(), product.getId());

        Optional<com.zelvira.zelvira.entity.ProductMarketplaceLink> linkOpt = productMarketplaceLinkRepository
                .findByProductIdAndMarketplaceType(product.getId(), "TIKTOK");

        if (linkOpt.isEmpty() || linkOpt.get().getMarketplaceProductId() == null
                || linkOpt.get().getMarketplaceProductId().isBlank()) {
            log.info("Product ID {} has no active link to TikTok Shop. Skipping auto-sync.", product.getId());
            return;
        }

        String remoteProductId = linkOpt.get().getMarketplaceProductId().trim();

        try {
            Map<String, Object> payload = new LinkedHashMap<>();

            // 1. Title (TikTok standard: 25 - 255 chars)
            String title = product.getName() != null ? product.getName().trim() : "Produk Zelvira";
            if (title.length() < 25) {
                title = "Zelvira - " + title + " Original Collection";
            }
            if (title.length() > 255) {
                title = title.substring(0, 255);
            }
            payload.put("title", title);

            // 2. Description
            if (product.getDescription() != null && !product.getDescription().isBlank()) {
                payload.put("description", product.getDescription().trim());
            }

            // 3. Images from product
            List<String> rawImageUrls = new ArrayList<>();
            if (product.getThumbnailUrl() != null && !product.getThumbnailUrl().isBlank()) {
                rawImageUrls.add(product.getThumbnailUrl().trim());
            }
            List<com.zelvira.zelvira.entity.ProductImage> images = productImageRepository
                    .findByProductIdOrderBySortOrderAsc(product.getId());
            if (images != null && !images.isEmpty()) {
                for (com.zelvira.zelvira.entity.ProductImage img : images) {
                    if (img.getUrl() != null && !img.getUrl().isBlank() && !rawImageUrls.contains(img.getUrl().trim())) {
                        rawImageUrls.add(img.getUrl().trim());
                    }
                }
            }
            if (!rawImageUrls.isEmpty()) {
                payload.put("images", rawImageUrls);
            }

            // 4. Execute partial_edit API call
            Map<String, Object> result = updateRemoteProduct(remoteProductId, payload);
            boolean success = Boolean.TRUE.equals(result.get("success"));

            com.zelvira.zelvira.entity.ProductMarketplaceLink link = linkOpt.get();
            link.setLastSyncTime(LocalDateTime.now());
            if (success) {
                link.setStatus("SYNCED");
                link.setErrorMessage(null);
                log.info("Successfully updated product '{}' on TikTok Shop (Remote ID: {})", product.getName(),
                        remoteProductId);
                productMarketplaceLinkRepository.save(link);
            } else {
                String errMsg = String.valueOf(result.get("message"));
                link.setStatus("FAILED");
                link.setErrorMessage(errMsg);
                productMarketplaceLinkRepository.save(link);
                log.warn("Failed to update product '{}' on TikTok Shop: {}", product.getName(), errMsg);
                throw new RuntimeException("Gagal update produk di TikTok Shop: " + errMsg);
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Exception during updateProduct for product ID {}: {}", product.getId(), e.getMessage(), e);
            throw new RuntimeException("Gagal sinkronisasi produk ke TikTok Shop: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public void updateStock(Product product, Integer newStock) {
        if (product == null || product.getId() == null)
            return;
        log.info("Synchronizing stock to TikTok Shop for product '{}' (ID: {})...", product.getName(), product.getId());

        Optional<MarketplaceConfig> configOpt = configRepository.findFirstByMarketplaceTypeAndIsActiveTrue("TIKTOK");
        if (configOpt.isEmpty()) {
            log.info("TikTok Shop is not connected. Skipping stock sync.");
            return;
        }

        MarketplaceConfig config = configOpt.get();
        Optional<com.zelvira.zelvira.entity.ProductMarketplaceLink> linkOpt = productMarketplaceLinkRepository
                .findByProductIdAndMarketplaceType(product.getId(), "TIKTOK");

        if (linkOpt.isEmpty() || linkOpt.get().getMarketplaceProductId() == null) {
            log.info("Product '{}' is not linked to TikTok Shop. Skipping stock sync.", product.getName());
            return;
        }

        String remoteProductId = linkOpt.get().getMarketplaceProductId();
        String cipher = ensureShopCipher(config);
        String warehouseId = getOrFetchDefaultWarehouseId(config);

        try {
            // 1. Fetch live product details to get remote SKU IDs
            String detailPath = "/product/202309/products/" + remoteProductId.trim();
            long timestamp = Instant.now().getEpochSecond();
            Map<String, String> qp = new LinkedHashMap<>();
            qp.put("app_key", appKey);
            qp.put("shop_cipher", cipher);
            qp.put("timestamp", String.valueOf(timestamp));
            String sign = generateSignature(detailPath, qp, "");
            String detailUrl = String.format("%s%s?app_key=%s&shop_cipher=%s&timestamp=%d&sign=%s",
                    apiBaseUrl, detailPath, appKey, cipher, timestamp, sign);

            HttpRequest detailReq = HttpRequest.newBuilder()
                    .uri(URI.create(detailUrl))
                    .header("Accept", "application/json")
                    .header("x-tts-access-token", config.getAccessToken())
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();

            HttpResponse<String> detailResp = httpClient.send(detailReq, HttpResponse.BodyHandlers.ofString());
            JsonNode detailRoot = objectMapper.readTree(detailResp.body());

            if (detailRoot.path("code").asInt(-1) != 0 || !detailRoot.has("data")) {
                log.warn("Could not get TikTok product details for stock update: {}", detailResp.body());
                return;
            }

            JsonNode dataNode = detailRoot.path("data");
            JsonNode skusNode = dataNode.path("skus");
            if (!skusNode.isArray() || skusNode.isEmpty()) {
                log.warn("No SKUs found on TikTok product ID {}", remoteProductId);
                return;
            }

            // 2. Map inventory update per SKU
            List<com.zelvira.zelvira.entity.ProductVariant> variants = productVariantRepository
                    .findByProductId(product.getId());
            Map<String, Integer> skuStockMap = new HashMap<>();
            for (com.zelvira.zelvira.entity.ProductVariant v : variants) {
                if (v.getSku() != null) {
                    skuStockMap.put(v.getSku().trim().toLowerCase(), v.getStock() != null ? v.getStock() : 0);
                }
            }

            List<Map<String, Object>> skusPayload = new ArrayList<>();
            for (JsonNode skuItem : skusNode) {
                String tiktokSkuId = skuItem.path("id").asText();
                String sellerSku = skuItem.path("seller_sku").asText("");

                int targetQty = newStock != null ? newStock : 0;
                if (!sellerSku.isBlank() && skuStockMap.containsKey(sellerSku.trim().toLowerCase())) {
                    targetQty = skuStockMap.get(sellerSku.trim().toLowerCase());
                } else if (!variants.isEmpty()) {
                    targetQty = variants.get(0).getStock() != null ? variants.get(0).getStock() : targetQty;
                }

                Map<String, Object> skuEntry = new LinkedHashMap<>();
                skuEntry.put("id", tiktokSkuId);

                Map<String, Object> inv = new LinkedHashMap<>();
                inv.put("quantity", targetQty);
                if (warehouseId != null && !warehouseId.isBlank()) {
                    inv.put("warehouse_id", warehouseId);
                }
                skuEntry.put("inventory", List.of(inv));
                skusPayload.add(skuEntry);
            }

            // 3. Send inventory update to TikTok Shop
            String invPath = "/product/202309/products/" + remoteProductId.trim() + "/inventory/update";
            Map<String, Object> invBody = Map.of("skus", skusPayload);
            String invJson = objectMapper.writeValueAsString(invBody);

            long invTimestamp = Instant.now().getEpochSecond();
            Map<String, String> invQp = new LinkedHashMap<>();
            invQp.put("app_key", appKey);
            invQp.put("shop_cipher", cipher);
            invQp.put("timestamp", String.valueOf(invTimestamp));

            String invSign = generateSignature(invPath, invQp, invJson);
            String invUrl = String.format("%s%s?app_key=%s&shop_cipher=%s&timestamp=%d&sign=%s",
                    apiBaseUrl, invPath, appKey, cipher, invTimestamp, invSign);

            HttpRequest invReq = HttpRequest.newBuilder()
                    .uri(URI.create(invUrl))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("x-tts-access-token", config.getAccessToken())
                    .timeout(Duration.ofSeconds(15))
                    .POST(HttpRequest.BodyPublishers.ofString(invJson))
                    .build();

            HttpResponse<String> invResp = httpClient.send(invReq, HttpResponse.BodyHandlers.ofString());
            log.info("TikTok stock update response for product '{}' (ID: {}): {}", product.getName(), remoteProductId,
                    invResp.body());

        } catch (Exception e) {
            log.error("Failed to update stock in TikTok for product {}: {}", product.getName(), e.getMessage());
        }
    }

    @Override
    @Transactional
    public Map<String, Object> getRemoteOrders(int pageSize, String pageToken, String orderStatus) {
        Optional<MarketplaceConfig> configOpt = configRepository.findFirstByMarketplaceTypeAndIsActiveTrue("TIKTOK");
        if (configOpt.isEmpty()) {
            List<com.zelvira.zelvira.entity.MarketplaceOrder> dbOrders = marketplaceOrderRepository
                    .findAllByMarketplaceTypeOrderByOrderCreatedAtDesc("TIKTOK");
            return Map.of(
                    "success", true,
                    "orders", dbOrders,
                    "total_count", dbOrders.size(),
                    "message", "TikTok belum terhubung. Menampilkan data lokal tersimpan.");
        }

        MarketplaceConfig config = configOpt.get();
        String cipher = ensureShopCipher(config);

        try {
            String path = "/order/202309/orders/search";
            long timestamp = Instant.now().getEpochSecond();

            Map<String, String> queryParams = new LinkedHashMap<>();
            queryParams.put("app_key", appKey);
            queryParams.put("shop_cipher", cipher);
            queryParams.put("timestamp", String.valueOf(timestamp));
            queryParams.put("page_size", String.valueOf(Math.min(pageSize, 100)));
            if (pageToken != null && !pageToken.isBlank()) {
                queryParams.put("page_token", pageToken.trim());
            }

            Map<String, Object> bodyMap = new HashMap<>();
            if (orderStatus != null && !orderStatus.isBlank() && !orderStatus.equalsIgnoreCase("ALL")) {
                bodyMap.put("order_status", orderStatus.trim());
            }
            String bodyJson = objectMapper.writeValueAsString(bodyMap);

            String sign = generateSignature(path, queryParams, bodyJson);
            StringBuilder urlBuilder = new StringBuilder(apiBaseUrl).append(path)
                    .append("?app_key=").append(appKey)
                    .append("&shop_cipher=").append(cipher)
                    .append("&timestamp=").append(timestamp)
                    .append("&page_size=").append(Math.min(pageSize, 100));
            if (pageToken != null && !pageToken.isBlank()) {
                urlBuilder.append("&page_token=").append(pageToken.trim());
            }
            urlBuilder.append("&sign=").append(sign);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(urlBuilder.toString()))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("x-tts-access-token", config.getAccessToken())
                    .timeout(Duration.ofSeconds(20))
                    .POST(HttpRequest.BodyPublishers.ofString(bodyJson))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("TikTok order search response: {}", response.body());

            JsonNode root = objectMapper.readTree(response.body());
            int code = root.path("code").asInt(-1);

            if (code == 0 && root.has("data")) {
                JsonNode data = root.path("data");
                JsonNode ordersArray = data.path("orders");
                String nextPageToken = data.path("next_page_token").asText("");
                int totalCount = data.path("total_count").asInt(0);

                List<com.zelvira.zelvira.entity.MarketplaceOrder> processedOrders = new ArrayList<>();

                if (ordersArray.isArray()) {
                    for (JsonNode oNode : ordersArray) {
                        String orderId = oNode.path("id").asText();
                        if (orderId.isBlank())
                            continue;

                        String status = oNode.path("status").asText("UNKNOWN");
                        long createTimeEpoch = oNode.path("create_time").asLong(Instant.now().getEpochSecond());
                        LocalDateTime orderCreatedAt = LocalDateTime.ofInstant(Instant.ofEpochSecond(createTimeEpoch),
                                java.time.ZoneId.systemDefault());

                        String buyerEmail = oNode.path("buyer_email").asText("");
                        JsonNode recipientNode = oNode.path("recipient_address");
                        String buyerName = recipientNode.path("name").asText("Pelanggan TikTok");
                        String buyerPhone = recipientNode.path("phone_number").asText("");
                        String shippingAddress = recipientNode.path("full_address").asText("");

                        double totalAmount = 0.0;
                        if (oNode.has("payment")) {
                            totalAmount = oNode.path("payment").path("total_amount").asDouble(0.0);
                        }

                        JsonNode lineItemsNode = oNode.path("line_items");
                        String lineItemsJson = lineItemsNode.toString();

                        com.zelvira.zelvira.entity.MarketplaceOrder orderEntity = marketplaceOrderRepository
                                .findByMarketplaceTypeAndMarketplaceOrderId("TIKTOK", orderId)
                                .orElse(com.zelvira.zelvira.entity.MarketplaceOrder.builder()
                                        .marketplaceType("TIKTOK")
                                        .marketplaceOrderId(orderId)
                                        .build());

                        orderEntity.setOrderStatus(status);
                        orderEntity.setBuyerName(buyerName);
                        orderEntity.setBuyerEmail(buyerEmail);
                        orderEntity.setBuyerPhone(buyerPhone);
                        orderEntity.setShippingAddress(shippingAddress);
                        orderEntity.setTotalAmount(totalAmount);
                        orderEntity.setLineItemsJson(lineItemsJson);
                        orderEntity.setRawPayload(oNode.toString());
                        orderEntity.setOrderCreatedAt(orderCreatedAt);

                        // Potong stok otomatis jika pesanan dibayar / siap kirim atau selesai
                        boolean isEligibleForDeduction = "AWAITING_SHIPMENT".equalsIgnoreCase(status)
                                || "AWAITING_COLLECTION".equalsIgnoreCase(status)
                                || "IN_TRANSIT".equalsIgnoreCase(status)
                                || "DELIVERED".equalsIgnoreCase(status)
                                || "COMPLETED".equalsIgnoreCase(status);

                        if (isEligibleForDeduction && !Boolean.TRUE.equals(orderEntity.getStockDeducted())) {
                            deductStockForOrderEntity(orderEntity, lineItemsNode);
                        } else if ("CANCELLED".equalsIgnoreCase(status)
                                && Boolean.TRUE.equals(orderEntity.getStockDeducted())) {
                            restoreStockForOrderEntity(orderEntity, lineItemsNode);
                        }

                        marketplaceOrderRepository.save(orderEntity);
                        processedOrders.add(orderEntity);
                    }
                }

                return Map.of(
                        "success", true,
                        "orders", processedOrders,
                        "total_count", totalCount,
                        "next_page_token", nextPageToken);
            }

            log.warn("TikTok order search failed with body: {}", response.body());

        } catch (Exception e) {
            log.error("Failed to query TikTok orders: {}", e.getMessage(), e);
        }

        // Fallback: return from DB
        List<com.zelvira.zelvira.entity.MarketplaceOrder> fallbackOrders = marketplaceOrderRepository
                .findAllByMarketplaceTypeOrderByOrderCreatedAtDesc("TIKTOK");
        return Map.of(
                "success", true,
                "orders", fallbackOrders,
                "total_count", fallbackOrders.size(),
                "fallback", true);
    }

    private void deductStockForOrderEntity(com.zelvira.zelvira.entity.MarketplaceOrder order, JsonNode lineItemsNode) {
        if (lineItemsNode == null || !lineItemsNode.isArray())
            return;

        log.info("Deducting master stock for TikTok Order: {}", order.getMarketplaceOrderId());
        boolean anyDeducted = false;

        for (JsonNode item : lineItemsNode) {
            String sellerSku = item.path("seller_sku").asText("").trim();
            int qty = item.path("quantity").asInt(1);
            com.zelvira.zelvira.entity.ProductVariant variant = null;

            if (!sellerSku.isBlank()) {
                variant = productVariantRepository.findBySku(sellerSku).orElse(null);
                if (variant == null) {
                    Optional<com.zelvira.zelvira.entity.Product> prodOpt = productRepository.findBySku(sellerSku);
                    if (prodOpt.isPresent()) {
                        List<com.zelvira.zelvira.entity.ProductVariant> vars = productVariantRepository
                                .findByProductId(prodOpt.get().getId());
                        if (!vars.isEmpty()) {
                            variant = vars.get(0);
                        }
                    }
                }
            }

            if (variant == null) {
                String remoteProdId = item.path("product_id").asText("");
                if (!remoteProdId.isBlank()) {
                    Optional<com.zelvira.zelvira.entity.ProductMarketplaceLink> link = productMarketplaceLinkRepository
                            .findByMarketplaceTypeAndMarketplaceProductId("TIKTOK", remoteProdId);
                    if (link.isPresent()) {
                        List<com.zelvira.zelvira.entity.ProductVariant> vars = productVariantRepository
                                .findByProductId(link.get().getProduct().getId());
                        if (!vars.isEmpty()) {
                            variant = vars.get(0);
                        }
                    }
                }
            }

            if (variant != null) {
                int oldStock = variant.getStock() != null ? variant.getStock() : 0;
                int newStock = Math.max(0, oldStock - qty);
                variant.setStock(newStock);
                productVariantRepository.save(variant);
                anyDeducted = true;
                log.info("Successfully deducted stock for SKU '{}' (Order: {}): {} -> {}",
                        variant.getSku(), order.getMarketplaceOrderId(), oldStock, newStock);
            }
        }

        if (anyDeducted) {
            order.setStockDeducted(true);
            order.setStockDeductedAt(LocalDateTime.now());
        }
    }

    private void restoreStockForOrderEntity(com.zelvira.zelvira.entity.MarketplaceOrder order, JsonNode lineItemsNode) {
        if (lineItemsNode == null || !lineItemsNode.isArray())
            return;

        log.info("Restoring master stock for cancelled TikTok Order: {}", order.getMarketplaceOrderId());

        for (JsonNode item : lineItemsNode) {
            String sellerSku = item.path("seller_sku").asText("").trim();
            int qty = item.path("quantity").asInt(1);
            com.zelvira.zelvira.entity.ProductVariant variant = null;

            if (!sellerSku.isBlank()) {
                variant = productVariantRepository.findBySku(sellerSku).orElse(null);
                if (variant == null) {
                    Optional<com.zelvira.zelvira.entity.Product> prodOpt = productRepository.findBySku(sellerSku);
                    if (prodOpt.isPresent()) {
                        List<com.zelvira.zelvira.entity.ProductVariant> vars = productVariantRepository
                                .findByProductId(prodOpt.get().getId());
                        if (!vars.isEmpty()) {
                            variant = vars.get(0);
                        }
                    }
                }
            }

            if (variant != null) {
                int oldStock = variant.getStock() != null ? variant.getStock() : 0;
                variant.setStock(oldStock + qty);
                productVariantRepository.save(variant);
                log.info("Restored stock for SKU '{}' (Order: {}): {} -> {}",
                        variant.getSku(), order.getMarketplaceOrderId(), oldStock, oldStock + qty);
            }
        }

        order.setStockDeducted(false);
        order.setStockDeductedAt(null);
    }

    @Override
    @Transactional
    public Map<String, Object> syncRemoteOrders() {
        Map<String, Object> result = getRemoteOrders(50, null, null);
        return Map.of(
                "success", true,
                "message", "Sinkronisasi pesanan TikTok berhasil diperbarui.",
                "data", result);
    }

    @Override
    @Transactional
    public Map<String, Object> deductStockForOrder(String orderId) {
        com.zelvira.zelvira.entity.MarketplaceOrder order = marketplaceOrderRepository
                .findByMarketplaceTypeAndMarketplaceOrderId("TIKTOK", orderId)
                .orElseThrow(
                        () -> new IllegalArgumentException("Pesanan TikTok dengan ID " + orderId + " tidak ditemukan"));

        if (Boolean.TRUE.equals(order.getStockDeducted())) {
            return Map.of(
                    "success", true,
                    "alreadyDeducted", true,
                    "message", "Stok untuk pesanan ini sudah pernah dipotong pada " + order.getStockDeductedAt());
        }

        try {
            JsonNode lineItemsNode = objectMapper.readTree(order.getLineItemsJson());
            deductStockForOrderEntity(order, lineItemsNode);
            order.setStockDeducted(true);
            order.setStockDeductedAt(LocalDateTime.now());
            marketplaceOrderRepository.save(order);

            return Map.of(
                    "success", true,
                    "message", "Stok berhasil dipotong untuk pesanan " + orderId);
        } catch (Exception e) {
            throw new RuntimeException("Gagal memotong stok pesanan: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public Map<String, Object> syncAllProductStocks() {
        List<com.zelvira.zelvira.entity.ProductMarketplaceLink> links = productMarketplaceLinkRepository
                .findAllByMarketplaceType("TIKTOK");

        int syncedCount = 0;
        for (com.zelvira.zelvira.entity.ProductMarketplaceLink link : links) {
            if (link.getProduct() != null && link.getMarketplaceProductId() != null) {
                try {
                    updateStock(link.getProduct(), null);
                    syncedCount++;
                } catch (Exception e) {
                    log.warn("Failed to push stock for product {}: {}", link.getProduct().getId(), e.getMessage());
                }
            }
        }

        return Map.of(
                "success", true,
                "synced_products", syncedCount,
                "total_links", links.size(),
                "message", "Berhasil sinkronisasi stok untuk " + syncedCount + " produk ke TikTok Shop.");
    }

    @Override
    @Transactional
    public Map<String, Object> syncSingleProductStock(Long productId) {
        com.zelvira.zelvira.entity.Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Produk tidak ditemukan dengan ID: " + productId));

        updateStock(product, null);
        return Map.of(
                "success", true,
                "message", "Stok produk '" + product.getName() + "' berhasil disinkronkan ke TikTok Shop.");
    }

    @Override
    public void deleteProduct(Product product) {
        log.info("Deleting product from TikTok: {}", product.getName());
    }

    /**
     * Retrieves the primary image URL for a remote product via GET
     * /product/202309/products/{product_id}.
     * Since the search endpoint only returns basic summaries, this fetches the re
     * /**
     * Queries product detail endpoint to fetch all main_images for a TikTok
     * product.
     */
    private List<Map<String, Object>> fetchRemoteProductAllImages(String productId, String cipher, String accessToken) {
        if (productId == null || productId.isBlank()) {
            return Collections.emptyList();
        }
        try {
            String path = "/product/202309/products/" + productId.trim();
            long timestamp = Instant.now().getEpochSecond();
            Map<String, String> queryParams = new LinkedHashMap<>();
            queryParams.put("app_key", appKey);
            queryParams.put("shop_cipher", cipher);
            queryParams.put("timestamp", String.valueOf(timestamp));

            String sign = generateSignature(path, queryParams, "");
            String url = String.format("%s%s?app_key=%s&shop_cipher=%s&timestamp=%d&sign=%s",
                    apiBaseUrl, path, appKey, cipher, timestamp, sign);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("x-tts-access-token", accessToken)
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("TikTok get product detail (id: {}) response: {}", productId, response.body());
            JsonNode root = objectMapper.readTree(response.body());
            if (root.path("code").asInt(-1) == 0 && root.has("data")) {
                JsonNode data = root.path("data");
                if (data.has("main_images") && data.path("main_images").isArray()) {
                    List<Map<String, Object>> result = new ArrayList<>();
                    for (JsonNode imgNode : data.path("main_images")) {
                        Map<String, Object> imgMap = new HashMap<>();
                        String uri = imgNode.path("uri").asText("");
                        imgMap.put("uri", uri);
                        List<String> urls = new ArrayList<>();
                        if (imgNode.has("urls") && imgNode.path("urls").isArray()) {
                            for (JsonNode u : imgNode.path("urls")) {
                                urls.add(u.asText());
                            }
                        }
                        if (urls.isEmpty() && imgNode.has("thumb_urls") && imgNode.path("thumb_urls").isArray()) {
                            for (JsonNode u : imgNode.path("thumb_urls")) {
                                urls.add(u.asText());
                            }
                        }
                        if (urls.isEmpty() && imgNode.has("url") && !imgNode.path("url").asText().isBlank()) {
                            urls.add(imgNode.path("url").asText());
                        }
                        if (urls.isEmpty() && !uri.isBlank()) {
                            urls.add(uri.startsWith("http") ? uri : ("https://p16-oec-va.ibyteimg.com/" + uri));
                        }
                        imgMap.put("urls", urls);
                        imgMap.put("url", urls.isEmpty() ? "" : urls.get(0));
                        result.add(imgMap);
                    }
                    return result;
                }
            }
        } catch (Exception e) {
            log.warn("Could not query product detail for all images (id: {}): {}", productId, e.getMessage());
        }
        return Collections.emptyList();
    }

    private String fetchRemoteProductMainImageUrl(String productId, String cipher, String accessToken) {
        List<Map<String, Object>> allImgs = fetchRemoteProductAllImages(productId, cipher, accessToken);
        if (!allImgs.isEmpty() && allImgs.get(0).containsKey("url")) {
            return String.valueOf(allImgs.get(0).get("url"));
        }
        return null;
    }

    @Override
    public Map<String, Object> getRemoteProducts(int pageSize, String pageToken) {
        log.info("Fetching products from TikTok Shop (pageSize: {}, pageToken: {})", pageSize, pageToken);
        Optional<MarketplaceConfig> configOpt = configRepository.findFirstByMarketplaceTypeAndIsActiveTrue("TIKTOK");
        if (configOpt.isEmpty()) {
            throw new IllegalStateException(
                    "Toko TikTok belum terhubung. Silakan hubungkan toko Anda terlebih dahulu.");
        }

        MarketplaceConfig config = configOpt.get();
        String cipher = ensureShopCipher(config);

        try {
            String path = "/product/202309/products/search";
            long timestamp = Instant.now().getEpochSecond();
            int limit = pageSize > 0 ? Math.min(pageSize, 100) : 20;

            Map<String, String> queryParams = new LinkedHashMap<>();
            queryParams.put("app_key", appKey);
            queryParams.put("page_size", String.valueOf(limit));
            queryParams.put("shop_cipher", cipher);
            queryParams.put("timestamp", String.valueOf(timestamp));

            Map<String, Object> bodyMap = new HashMap<>();
            if (pageToken != null && !pageToken.isBlank()) {
                bodyMap.put("page_token", pageToken.trim());
            }
            String bodyJson = objectMapper.writeValueAsString(bodyMap);

            String sign = generateSignature(path, queryParams, bodyJson);
            String url = String.format("%s%s?app_key=%s&page_size=%d&shop_cipher=%s&timestamp=%d&sign=%s",
                    apiBaseUrl, path, appKey, limit, cipher, timestamp, sign);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("x-tts-access-token", config.getAccessToken())
                    .timeout(Duration.ofSeconds(20))
                    .POST(HttpRequest.BodyPublishers.ofString(bodyJson))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("TikTok search products response: {}", response.body());

            JsonNode root = objectMapper.readTree(response.body());
            int code = root.path("code").asInt(-1);
            if (code != 0) {
                String msg = root.path("message").asText("Failed to search products");
                log.error("TikTok search products returned error: {}", msg);
                return Map.of("success", false, "message", msg, "products", List.of(), "total_count", 0);
            }

            JsonNode data = root.path("data");
            List<Map<String, Object>> productList = new ArrayList<>();
            if (data.has("products") && data.path("products").isArray()) {
                for (JsonNode p : data.path("products")) {
                    String status = p.path("status").asText("");
                    // Filter out deleted products: only active and inactive should be listed
                    if ("DELETED".equalsIgnoreCase(status) || status.toUpperCase().contains("DELETE")) {
                        continue;
                    }

                    Map<String, Object> item = new HashMap<>();
                    item.put("id", p.path("id").asText());
                    item.put("title", p.path("title").asText());
                    item.put("status", status);
                    item.put("create_time", p.path("create_time").asLong());
                    item.put("update_time", p.path("update_time").asLong());

                    // Main images
                    List<Map<String, Object>> mainImagesList = new ArrayList<>();
                    if (p.has("main_images") && p.path("main_images").isArray()) {
                        for (JsonNode imgNode : p.path("main_images")) {
                            Map<String, Object> imgMap = new HashMap<>();
                            String uri = imgNode.path("uri").asText("");
                            imgMap.put("uri", uri);
                            List<String> urls = new ArrayList<>();
                            if (imgNode.has("urls") && imgNode.path("urls").isArray()) {
                                for (JsonNode u : imgNode.path("urls")) {
                                    urls.add(u.asText());
                                }
                            }
                            if (urls.isEmpty() && imgNode.has("thumb_urls") && imgNode.path("thumb_urls").isArray()) {
                                for (JsonNode u : imgNode.path("thumb_urls")) {
                                    urls.add(u.asText());
                                }
                            }
                            if (urls.isEmpty() && imgNode.has("url") && !imgNode.path("url").asText().isBlank()) {
                                urls.add(imgNode.path("url").asText());
                            }
                            if (urls.isEmpty() && !uri.isBlank()) {
                                urls.add(uri.startsWith("http") ? uri : ("https://p16-oec-va.ibyteimg.com/" + uri));
                            }
                            imgMap.put("urls", urls);
                            imgMap.put("url", urls.isEmpty() ? "" : urls.get(0));
                            mainImagesList.add(imgMap);
                        }
                    }

                    // If main_images was empty from search, query product detail
                    if (mainImagesList.isEmpty()) {
                        List<Map<String, Object>> detailImgs = fetchRemoteProductAllImages(p.path("id").asText(),
                                cipher,
                                config.getAccessToken());
                        if (detailImgs != null && !detailImgs.isEmpty()) {
                            mainImagesList.addAll(detailImgs);
                        }
                    }

                    item.put("main_images", mainImagesList);
                    if (!mainImagesList.isEmpty()) {
                        item.put("image_url", mainImagesList.get(0).get("url"));
                    }

                    // SKUs
                    List<Map<String, Object>> skus = new ArrayList<>();
                    if (p.has("skus") && p.path("skus").isArray()) {
                        for (JsonNode skuNode : p.path("skus")) {
                            Map<String, Object> sku = new HashMap<>();
                            sku.put("id", skuNode.path("id").asText());
                            sku.put("seller_sku", skuNode.path("seller_sku").asText());
                            if (skuNode.has("price")) {
                                JsonNode priceNode = skuNode.path("price");
                                String amountStr = "";
                                if (priceNode.has("amount") && !priceNode.path("amount").asText().isBlank()) {
                                    amountStr = priceNode.path("amount").asText();
                                } else if (priceNode.has("sale_price")
                                        && !priceNode.path("sale_price").asText().isBlank()) {
                                    amountStr = priceNode.path("sale_price").asText();
                                } else if (priceNode.has("price") && !priceNode.path("price").asText().isBlank()) {
                                    amountStr = priceNode.path("price").asText();
                                } else if (priceNode.has("tax_exclusive_price")
                                        && !priceNode.path("tax_exclusive_price").asText().isBlank()) {
                                    amountStr = priceNode.path("tax_exclusive_price").asText();
                                }
                                sku.put("price", amountStr);
                                sku.put("currency", priceNode.path("currency").asText("IDR"));
                            } else if (skuNode.has("list_price")) {
                                sku.put("price", skuNode.path("list_price").path("price").asText(""));
                                sku.put("currency", skuNode.path("list_price").path("currency").asText("IDR"));
                            }
                            if (skuNode.has("inventory") && skuNode.path("inventory").isArray()
                                    && skuNode.path("inventory").size() > 0) {
                                JsonNode inv = skuNode.path("inventory").get(0);
                                sku.put("quantity", inv.path("quantity").asInt(0));
                                sku.put("warehouse_id", inv.path("warehouse_id").asText(""));
                            }
                            skus.add(sku);
                        }
                    }
                    item.put("skus", skus);
                    productList.add(item);
                }
            }

            return Map.of(
                    "success", true,
                    "products", productList,
                    "total_count", productList.size(),
                    "next_page_token", data.path("next_page_token").asText(""));
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Exception fetching TikTok products: {}", e.getMessage(), e);
            throw new RuntimeException("Gagal mengambil data produk dari TikTok Shop: " + e.getMessage(), e);
        }
    }

    @Override
    public Map<String, Object> updateRemoteProduct(String remoteProductId, Map<String, Object> payload) {
        log.info("Updating product {} on TikTok Shop with payload: {}", remoteProductId, payload);
        Optional<MarketplaceConfig> configOpt = configRepository.findFirstByMarketplaceTypeAndIsActiveTrue("TIKTOK");
        if (configOpt.isEmpty()) {
            throw new IllegalStateException(
                    "Toko TikTok belum terhubung. Silakan hubungkan toko Anda terlebih dahulu.");
        }

        MarketplaceConfig config = configOpt.get();
        ensureValidAccessToken(config);
        String cipher = ensureShopCipher(config);

        try {
            // Auto-inject warehouse_id if missing in skus inventory
            Map<String, Object> finalPayload = new HashMap<>(payload);
            if (finalPayload.containsKey("skus") && finalPayload.get("skus") instanceof List<?> skuList) {
                String defaultWarehouseId = null;
                List<Map<String, Object>> fixedSkus = new ArrayList<>();
                for (Object s : skuList) {
                    if (s instanceof Map<?, ?> skuMap) {
                        Map<String, Object> copySku = new HashMap<>();
                        for (Map.Entry<?, ?> e : skuMap.entrySet()) {
                            copySku.put(String.valueOf(e.getKey()), e.getValue());
                        }
                        if (copySku.containsKey("inventory") && copySku.get("inventory") instanceof List<?> invList) {
                            List<Map<String, Object>> fixedInv = new ArrayList<>();
                            for (Object inv : invList) {
                                if (inv instanceof Map<?, ?> invMap) {
                                    Map<String, Object> copyInv = new HashMap<>();
                                    for (Map.Entry<?, ?> ie : invMap.entrySet()) {
                                        copyInv.put(String.valueOf(ie.getKey()), ie.getValue());
                                    }
                                    if (!copyInv.containsKey("warehouse_id") || copyInv.get("warehouse_id") == null
                                            || String.valueOf(copyInv.get("warehouse_id")).isBlank()) {
                                        if (defaultWarehouseId == null) {
                                            defaultWarehouseId = getOrFetchDefaultWarehouseId(config);
                                        }
                                        if (defaultWarehouseId != null && !defaultWarehouseId.isBlank()) {
                                            copyInv.put("warehouse_id", defaultWarehouseId);
                                        }
                                    }
                                    fixedInv.add(copyInv);
                                }
                            }
                            copySku.put("inventory", fixedInv);
                        }
                        fixedSkus.add(copySku);
                    }
                }
                finalPayload.put("skus", fixedSkus);
            }

            // Process main_images or imageUrl if provided
            List<String> rawImageUrls = new ArrayList<>();
            if (finalPayload.containsKey("main_images") && finalPayload.get("main_images") instanceof List<?> imgList) {
                for (Object item : imgList) {
                    if (item instanceof String s && !s.isBlank()) {
                        if (!rawImageUrls.contains(s.trim()))
                            rawImageUrls.add(s.trim());
                    } else if (item instanceof Map<?, ?> map) {
                        Object uri = map.get("uri");
                        if (uri == null || String.valueOf(uri).isBlank())
                            uri = map.get("url");
                        if (uri != null && !String.valueOf(uri).isBlank()) {
                            String s = String.valueOf(uri).trim();
                            if (!rawImageUrls.contains(s))
                                rawImageUrls.add(s);
                        }
                    }
                }
            } else if (finalPayload.containsKey("images") && finalPayload.get("images") instanceof List<?> imgList) {
                for (Object item : imgList) {
                    if (item instanceof String s && !s.isBlank()) {
                        if (!rawImageUrls.contains(s.trim()))
                            rawImageUrls.add(s.trim());
                    } else if (item instanceof Map<?, ?> map) {
                        Object uri = map.get("uri");
                        if (uri == null || String.valueOf(uri).isBlank())
                            uri = map.get("url");
                        if (uri != null && !String.valueOf(uri).isBlank()) {
                            String s = String.valueOf(uri).trim();
                            if (!rawImageUrls.contains(s))
                                rawImageUrls.add(s);
                        }
                    }
                }
            } else {
                if (finalPayload.containsKey("imageUrl") && finalPayload.get("imageUrl") != null) {
                    String u = String.valueOf(finalPayload.get("imageUrl")).trim();
                    if (!u.isBlank() && !rawImageUrls.contains(u))
                        rawImageUrls.add(u);
                }
                if (finalPayload.containsKey("image_url") && finalPayload.get("image_url") != null) {
                    String u = String.valueOf(finalPayload.get("image_url")).trim();
                    if (!u.isBlank() && !rawImageUrls.contains(u))
                        rawImageUrls.add(u);
                }
                if (finalPayload.containsKey("thumbnailUrl") && finalPayload.get("thumbnailUrl") != null) {
                    String u = String.valueOf(finalPayload.get("thumbnailUrl")).trim();
                    if (!u.isBlank() && !rawImageUrls.contains(u))
                        rawImageUrls.add(u);
                }
                if (finalPayload.containsKey("thumbnail_url") && finalPayload.get("thumbnail_url") != null) {
                    String u = String.valueOf(finalPayload.get("thumbnail_url")).trim();
                    if (!u.isBlank() && !rawImageUrls.contains(u))
                        rawImageUrls.add(u);
                }
            }

            if (!rawImageUrls.isEmpty()) {
                List<Map<String, String>> updatedMainImages = new ArrayList<>();
                for (String imgUrl : rawImageUrls) {
                    if (imgUrl.startsWith("tos-")) {
                        updatedMainImages.add(Map.of("uri", imgUrl));
                    } else {
                        String uploadedUri = uploadProductImage(imgUrl, cipher, config.getAccessToken());
                        if (uploadedUri != null && !uploadedUri.isBlank()) {
                            updatedMainImages.add(Map.of("uri", uploadedUri));
                        }
                    }
                    if (updatedMainImages.size() >= 9)
                        break;
                }
                if (!updatedMainImages.isEmpty()) {
                    finalPayload.put("main_images", updatedMainImages);
                    log.info("Set main_images in TikTok partial_edit for product {}: {}", remoteProductId,
                            updatedMainImages);
                }
            }

            // Clean up non-TikTok partial_edit keys from finalPayload
            finalPayload.remove("imageUrl");
            finalPayload.remove("image_url");
            finalPayload.remove("images");
            finalPayload.remove("thumbnailUrl");
            finalPayload.remove("thumbnail_url");

            // Partial Edit Product 2.0: POST
            // /product/202309/products/{product_id}/partial_edit
            String path = "/product/202309/products/" + remoteProductId.trim() + "/partial_edit";
            long timestamp = Instant.now().getEpochSecond();

            Map<String, String> queryParams = new LinkedHashMap<>();
            queryParams.put("app_key", appKey);
            queryParams.put("shop_cipher", cipher);
            queryParams.put("timestamp", String.valueOf(timestamp));

            String bodyJson = objectMapper.writeValueAsString(finalPayload);
            String sign = generateSignature(path, queryParams, bodyJson);
            String url = String.format("%s%s?app_key=%s&shop_cipher=%s&timestamp=%d&sign=%s",
                    apiBaseUrl, path, appKey, cipher, timestamp, sign);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("x-tts-access-token", config.getAccessToken())
                    .timeout(Duration.ofSeconds(20))
                    .POST(HttpRequest.BodyPublishers.ofString(bodyJson))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("TikTok partial edit product response: {}", response.body());

            JsonNode root = objectMapper.readTree(response.body());
            int code = root.path("code").asInt(-1);

            // If token expired, auto-refresh and retry once
            if (isTokenExpiredError(code, root.path("message").asText()) && refreshAccessToken(config)) {
                log.info("Access token refreshed after token error in updateRemoteProduct. Retrying...");
                timestamp = Instant.now().getEpochSecond();
                queryParams.put("timestamp", String.valueOf(timestamp));
                sign = generateSignature(path, queryParams, bodyJson);
                url = String.format("%s%s?app_key=%s&shop_cipher=%s&timestamp=%d&sign=%s",
                        apiBaseUrl, path, appKey, cipher, timestamp, sign);
                request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Content-Type", "application/json")
                        .header("Accept", "application/json")
                        .header("x-tts-access-token", config.getAccessToken())
                        .timeout(Duration.ofSeconds(20))
                        .POST(HttpRequest.BodyPublishers.ofString(bodyJson))
                        .build();
                response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                log.info("TikTok partial edit product retry response: {}", response.body());
                root = objectMapper.readTree(response.body());
                code = root.path("code").asInt(-1);
            }

            if (code != 0) {
                String msg = root.path("message").asText("Gagal memperbarui produk di TikTok");
                log.error("TikTok update product returned error: code={}, message={}, body={}", code, msg, response.body());
                return Map.of("success", false, "message", msg, "code", code);
            }

            return Map.of("success", true, "message", "Produk di TikTok Shop berhasil diperbarui!");
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Exception updating TikTok product: {}", e.getMessage(), e);
            throw new RuntimeException("Gagal memperbarui produk di TikTok Shop: " + e.getMessage(), e);
        }
    }

    @Override
    public Map<String, Object> activateRemoteProduct(String remoteProductId) {
        log.info("Activating product {} on TikTok Shop", remoteProductId);
        Optional<MarketplaceConfig> configOpt = configRepository.findFirstByMarketplaceTypeAndIsActiveTrue("TIKTOK");
        if (configOpt.isEmpty()) {
            throw new IllegalStateException("Toko TikTok belum terhubung.");
        }

        MarketplaceConfig config = configOpt.get();
        String cipher = ensureShopCipher(config);

        try {
            String path = "/product/202309/products/activate";
            long timestamp = Instant.now().getEpochSecond();

            Map<String, String> queryParams = new LinkedHashMap<>();
            queryParams.put("app_key", appKey);
            queryParams.put("shop_cipher", cipher);
            queryParams.put("timestamp", String.valueOf(timestamp));

            Map<String, Object> bodyMap = Map.of("product_ids", List.of(remoteProductId.trim()));
            String bodyJson = objectMapper.writeValueAsString(bodyMap);
            String sign = generateSignature(path, queryParams, bodyJson);

            String url = String.format("%s%s?app_key=%s&shop_cipher=%s&timestamp=%d&sign=%s",
                    apiBaseUrl, path, appKey, cipher, timestamp, sign);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("x-tts-access-token", config.getAccessToken())
                    .timeout(Duration.ofSeconds(20))
                    .POST(HttpRequest.BodyPublishers.ofString(bodyJson))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("TikTok activate product response: {}", response.body());

            JsonNode root = objectMapper.readTree(response.body());
            int code = root.path("code").asInt(-1);
            if (code != 0) {
                String msg = root.path("message").asText("Gagal mengaktifkan produk di TikTok Shop");
                log.error("TikTok activate product returned error: {}", msg);
                return Map.of("success", false, "message", msg);
            }

            return Map.of("success", true, "message", "Produk di TikTok Shop berhasil diaktifkan kembali!");
        } catch (Exception e) {
            log.error("Exception activating TikTok product: {}", e.getMessage(), e);
            throw new RuntimeException("Gagal mengaktifkan produk di TikTok Shop: " + e.getMessage(), e);
        }
    }

    @Override
    public Map<String, Object> deactivateRemoteProduct(String remoteProductId) {
        log.info("Deactivating product {} on TikTok Shop", remoteProductId);
        Optional<MarketplaceConfig> configOpt = configRepository.findFirstByMarketplaceTypeAndIsActiveTrue("TIKTOK");
        if (configOpt.isEmpty()) {
            throw new IllegalStateException("Toko TikTok belum terhubung.");
        }

        MarketplaceConfig config = configOpt.get();
        String cipher = ensureShopCipher(config);

        try {
            String path = "/product/202309/products/deactivate";
            long timestamp = Instant.now().getEpochSecond();

            Map<String, String> queryParams = new LinkedHashMap<>();
            queryParams.put("app_key", appKey);
            queryParams.put("shop_cipher", cipher);
            queryParams.put("timestamp", String.valueOf(timestamp));

            Map<String, Object> bodyMap = Map.of("product_ids", List.of(remoteProductId.trim()));
            String bodyJson = objectMapper.writeValueAsString(bodyMap);
            String sign = generateSignature(path, queryParams, bodyJson);

            String url = String.format("%s%s?app_key=%s&shop_cipher=%s&timestamp=%d&sign=%s",
                    apiBaseUrl, path, appKey, cipher, timestamp, sign);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("x-tts-access-token", config.getAccessToken())
                    .timeout(Duration.ofSeconds(20))
                    .POST(HttpRequest.BodyPublishers.ofString(bodyJson))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("TikTok deactivate product response: {}", response.body());

            JsonNode root = objectMapper.readTree(response.body());
            int code = root.path("code").asInt(-1);
            if (code != 0) {
                String msg = root.path("message").asText("Gagal menonaktifkan produk di TikTok Shop");
                log.error("TikTok deactivate product returned error: {}", msg);
                return Map.of("success", false, "message", msg);
            }

            return Map.of("success", true, "message", "Produk di TikTok Shop berhasil dinonaktifkan!");
        } catch (Exception e) {
            log.error("Exception deactivating TikTok product: {}", e.getMessage(), e);
            throw new RuntimeException("Gagal menonaktifkan produk di TikTok Shop: " + e.getMessage(), e);
        }
    }

    @Override
    public Map<String, Object> deleteRemoteProduct(String remoteProductId) {
        log.info("Deleting product {} on TikTok Shop", remoteProductId);
        Optional<MarketplaceConfig> configOpt = configRepository.findFirstByMarketplaceTypeAndIsActiveTrue("TIKTOK");
        if (configOpt.isEmpty()) {
            throw new IllegalStateException("Toko TikTok belum terhubung.");
        }

        MarketplaceConfig config = configOpt.get();
        String cipher = ensureShopCipher(config);

        try {
            String path = "/product/202309/products";
            long timestamp = Instant.now().getEpochSecond();

            Map<String, String> queryParams = new LinkedHashMap<>();
            queryParams.put("app_key", appKey);
            queryParams.put("shop_cipher", cipher);
            queryParams.put("timestamp", String.valueOf(timestamp));

            Map<String, Object> bodyMap = Map.of("product_ids", List.of(remoteProductId.trim()));
            String bodyJson = objectMapper.writeValueAsString(bodyMap);
            String sign = generateSignature(path, queryParams, bodyJson);

            String url = String.format("%s%s?app_key=%s&shop_cipher=%s&timestamp=%d&sign=%s",
                    apiBaseUrl, path, appKey, cipher, timestamp, sign);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("x-tts-access-token", config.getAccessToken())
                    .timeout(Duration.ofSeconds(20))
                    .method("DELETE", HttpRequest.BodyPublishers.ofString(bodyJson))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("TikTok delete product response: {}", response.body());

            JsonNode root = objectMapper.readTree(response.body());
            int code = root.path("code").asInt(-1);
            if (code != 0) {
                String msg = root.path("message").asText("Gagal menghapus produk di TikTok Shop");
                log.error("TikTok delete product returned error: {}", msg);
                return Map.of("success", false, "message", msg);
            }

            return Map.of("success", true, "message", "Produk di TikTok Shop berhasil dihapus!");
        } catch (Exception e) {
            log.error("Exception deleting TikTok product: {}", e.getMessage(), e);
            throw new RuntimeException("Gagal menghapus produk di TikTok Shop: " + e.getMessage(), e);
        }
    }

    /**
     * Calculates HMAC-SHA256 signature according to TikTok Shop Open API
     * documentation.
     * Rules:
     * 1. Extract query params excluding 'sign' and 'access_token'
     * 2. Sort keys alphabetically
     * 3. Concatenate key + value
     * 4. Prepend path, append body
     * 5. Wrap with app_secret at start and end
     * 6. Compute HMAC-SHA256 with app_secret, output lowercase hex
     */
    public String generateSignature(String path, Map<String, String> queryParams, String body) {
        try {
            Map<String, String> filtered = new HashMap<>();
            if (queryParams != null) {
                for (Map.Entry<String, String> entry : queryParams.entrySet()) {
                    if (!"sign".equalsIgnoreCase(entry.getKey()) && !"access_token".equalsIgnoreCase(entry.getKey())) {
                        filtered.put(entry.getKey(), entry.getValue());
                    }
                }
            }

            List<String> sortedKeys = new ArrayList<>(filtered.keySet());
            Collections.sort(sortedKeys);

            StringBuilder queryBuilder = new StringBuilder();
            for (String key : sortedKeys) {
                queryBuilder.append(key).append(filtered.get(key));
            }

            String signString = path + queryBuilder.toString() + (body != null ? body : "");
            String finalPayload = appSecret + signString + appSecret;

            Mac sha256HMAC = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            sha256HMAC.init(secretKey);

            byte[] hash = sha256HMAC.doFinal(finalPayload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                hexString.append(String.format("%02x", b));
            }

            return hexString.toString();
        } catch (Exception e) {
            log.error("Failed to generate TikTok Shop signature: {}", e.getMessage());
            return "";
        }
    }

    @Override
    public Map<String, Object> uploadImage(org.springframework.web.multipart.MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File gambar tidak boleh kosong");
        }
        Optional<MarketplaceConfig> configOpt = configRepository.findFirstByMarketplaceTypeAndIsActiveTrue("TIKTOK");
        if (configOpt.isEmpty()) {
            throw new IllegalStateException("Toko TikTok belum terhubung. Hubungkan toko terlebih dahulu.");
        }
        MarketplaceConfig config = configOpt.get();
        ensureValidAccessToken(config);
        String cipher = ensureShopCipher(config);
        try {
            byte[] bytes = file.getBytes();
            String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "product.jpg";
            String uri = uploadProductImageBytes(bytes, originalFilename, "MAIN_IMAGE", cipher, config.getAccessToken());
            if (uri == null || uri.isBlank()) {
                String detail = lastUploadErrorMessage != null ? lastUploadErrorMessage
                        : "Gagal mengunggah gambar ke TikTok Shop";
                return Map.of("success", false, "message", detail);
            }
            String previewUrl = uri.startsWith("tos-") ? "https://p16-oec-va.ibyteimg.com/" + uri : uri;
            log.info("Directly uploaded image to TikTok Shop, uri: {}, preview: {}", uri, previewUrl);
            return Map.of(
                    "success", true,
                    "uri", uri,
                    "url", previewUrl,
                    "message", "Gambar berhasil diunggah langsung ke TikTok Shop!");
        } catch (Exception e) {
            log.error("Failed to upload image file directly to TikTok Shop: {}", e.getMessage(), e);
            throw new RuntimeException("Gagal mengunggah gambar ke TikTok: " + e.getMessage(), e);
        }
    }

    @Override
    public Map<String, Object> getChatConversations(int pageSize, String pageToken) {
        log.info("Fetching customer service chat conversations from TikTok Shop...");
        Optional<MarketplaceConfig> configOpt = configRepository.findFirstByMarketplaceTypeAndIsActiveTrue("TIKTOK");
        if (configOpt.isEmpty()) {
            return Map.of(
                    "success", false,
                    "connected", false,
                    "message", "Toko TikTok belum terhubung. Silakan hubungkan toko di menu Integrasi.");
        }

        MarketplaceConfig config = configOpt.get();
        ensureValidAccessToken(config);
        String cipher = ensureShopCipher(config);

        try {
            String path = "/customer_service/202309/conversations";
            long timestamp = Instant.now().getEpochSecond();
            int limit = pageSize > 0 ? Math.min(pageSize, 50) : 20;

            Map<String, String> queryParams = new LinkedHashMap<>();
            queryParams.put("app_key", appKey);
            queryParams.put("page_size", String.valueOf(limit));
            queryParams.put("shop_cipher", cipher);
            queryParams.put("timestamp", String.valueOf(timestamp));
            if (pageToken != null && !pageToken.isBlank()) {
                queryParams.put("page_token", pageToken.trim());
            }

            String sign = generateSignature(path, queryParams, "");
            String pageTokenParam = (pageToken != null && !pageToken.isBlank()) ? "&page_token=" + pageToken.trim() : "";
            String url = String.format("%s%s?app_key=%s&page_size=%d&shop_cipher=%s&timestamp=%d%s&sign=%s",
                    apiBaseUrl, path, appKey, limit, cipher, timestamp, pageTokenParam, sign);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Accept", "application/json")
                    .header("x-tts-access-token", config.getAccessToken())
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("TikTok get conversations response: {}", response.body());

            JsonNode root = objectMapper.readTree(response.body());
            int code = root.path("code").asInt(-1);

            if (isTokenExpiredError(code, root.path("message").asText()) && refreshAccessToken(config)) {
                timestamp = Instant.now().getEpochSecond();
                queryParams.put("timestamp", String.valueOf(timestamp));
                sign = generateSignature(path, queryParams, "");
                url = String.format("%s%s?app_key=%s&page_size=%d&shop_cipher=%s&timestamp=%d%s&sign=%s",
                        apiBaseUrl, path, appKey, limit, cipher, timestamp, pageTokenParam, sign);
                request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Accept", "application/json")
                        .header("x-tts-access-token", config.getAccessToken())
                        .timeout(Duration.ofSeconds(15))
                        .GET()
                        .build();
                response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                root = objectMapper.readTree(response.body());
                code = root.path("code").asInt(-1);
            }

            if (code != 0) {
                String errMsg = root.path("message").asText("Tidak dapat memuat percakapan TikTok Shop");
                boolean permReq = errMsg.toLowerCase().contains("permission") || errMsg.toLowerCase().contains("scope")
                        || code == 36000000 || code == 36000001;
                return Map.of(
                        "success", false,
                        "permission_required", permReq,
                        "code", code,
                        "message", permReq
                                ? "Fitur Chat TikTok memerlukan izin 'seller.customer_service' di TikTok Shop Partner Center."
                                : errMsg,
                        "conversations", Collections.emptyList());
            }

            JsonNode data = root.path("data");
            List<Map<String, Object>> convList = new ArrayList<>();
            JsonNode convNodes = data.has("conversations") ? data.path("conversations") : null;
            if (convNodes != null && convNodes.isArray()) {
                for (JsonNode cn : convNodes) {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", cn.path("id").asText(""));
                    map.put("conversation_id", cn.path("id").asText(""));
                    map.put("buyer_name", cn.path("participant").path("name").asText("TikTok Buyer"));
                    map.put("buyer_avatar", cn.path("participant").path("avatar").asText(""));
                    map.put("unread_count", cn.path("unread_count").asInt(0));
                    map.put("latest_message", cn.path("latest_message").path("content").asText(""));
                    map.put("latest_message_time", cn.path("latest_message").path("create_time").asLong(0));
                    map.put("status", cn.path("status").asText("ACTIVE"));
                    convList.add(map);
                }
            }

            return Map.of(
                    "success", true,
                    "connected", true,
                    "conversations", convList,
                    "total_count", convList.size(),
                    "next_page_token", data.path("next_page_token").asText(""));

        } catch (Exception e) {
            log.error("Failed to fetch TikTok chat conversations: {}", e.getMessage(), e);
            return Map.of(
                    "success", false,
                    "message", "Gagal memuat percakapan TikTok: " + e.getMessage(),
                    "conversations", Collections.emptyList());
        }
    }

    @Override
    public Map<String, Object> getChatMessages(String conversationId, int pageSize, String pageToken) {
        log.info("Fetching TikTok chat messages for conversation {}...", conversationId);
        Optional<MarketplaceConfig> configOpt = configRepository.findFirstByMarketplaceTypeAndIsActiveTrue("TIKTOK");
        if (configOpt.isEmpty()) {
            return Map.of("success", false, "message", "Toko TikTok belum terhubung.");
        }

        MarketplaceConfig config = configOpt.get();
        ensureValidAccessToken(config);
        String cipher = ensureShopCipher(config);

        try {
            String path = "/customer_service/202309/conversations/" + conversationId.trim() + "/messages";
            long timestamp = Instant.now().getEpochSecond();
            int limit = pageSize > 0 ? Math.min(pageSize, 100) : 50;

            Map<String, String> queryParams = new LinkedHashMap<>();
            queryParams.put("app_key", appKey);
            queryParams.put("page_size", String.valueOf(limit));
            queryParams.put("shop_cipher", cipher);
            queryParams.put("timestamp", String.valueOf(timestamp));
            if (pageToken != null && !pageToken.isBlank()) {
                queryParams.put("page_token", pageToken.trim());
            }

            String sign = generateSignature(path, queryParams, "");
            String pageTokenParam = (pageToken != null && !pageToken.isBlank()) ? "&page_token=" + pageToken.trim() : "";
            String url = String.format("%s%s?app_key=%s&page_size=%d&shop_cipher=%s&timestamp=%d%s&sign=%s",
                    apiBaseUrl, path, appKey, limit, cipher, timestamp, pageTokenParam, sign);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Accept", "application/json")
                    .header("x-tts-access-token", config.getAccessToken())
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode root = objectMapper.readTree(response.body());
            int code = root.path("code").asInt(-1);

            if (isTokenExpiredError(code, root.path("message").asText()) && refreshAccessToken(config)) {
                timestamp = Instant.now().getEpochSecond();
                queryParams.put("timestamp", String.valueOf(timestamp));
                sign = generateSignature(path, queryParams, "");
                url = String.format("%s%s?app_key=%s&page_size=%d&shop_cipher=%s&timestamp=%d%s&sign=%s",
                        apiBaseUrl, path, appKey, limit, cipher, timestamp, pageTokenParam, sign);
                request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Accept", "application/json")
                        .header("x-tts-access-token", config.getAccessToken())
                        .timeout(Duration.ofSeconds(15))
                        .GET()
                        .build();
                response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                root = objectMapper.readTree(response.body());
                code = root.path("code").asInt(-1);
            }

            if (code != 0) {
                return Map.of("success", false, "message", root.path("message").asText("Gagal memuat pesan"), "messages", Collections.emptyList());
            }

            JsonNode data = root.path("data");
            List<Map<String, Object>> msgList = new ArrayList<>();
            JsonNode msgsNode = data.has("messages") ? data.path("messages") : null;
            if (msgsNode != null && msgsNode.isArray()) {
                for (JsonNode mn : msgsNode) {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", mn.path("id").asText(""));
                    map.put("content", mn.path("content").asText(""));
                    map.put("type", mn.path("type").asText("TEXT"));
                    map.put("create_time", mn.path("create_time").asLong(0));
                    map.put("sender_role", mn.path("sender").path("role").asText("BUYER"));
                    map.put("sender_name", mn.path("sender").path("name").asText(""));
                    msgList.add(map);
                }
            }

            return Map.of(
                    "success", true,
                    "messages", msgList,
                    "next_page_token", data.path("next_page_token").asText(""));

        } catch (Exception e) {
            log.error("Failed to fetch messages for conversation {}: {}", conversationId, e.getMessage(), e);
            return Map.of("success", false, "message", e.getMessage(), "messages", Collections.emptyList());
        }
    }

    @Override
    public Map<String, Object> sendChatMessage(String conversationId, String content, String messageType) {
        log.info("Sending message to TikTok conversation {}: {}", conversationId, content);
        Optional<MarketplaceConfig> configOpt = configRepository.findFirstByMarketplaceTypeAndIsActiveTrue("TIKTOK");
        if (configOpt.isEmpty()) {
            return Map.of("success", false, "message", "Toko TikTok belum terhubung.");
        }

        MarketplaceConfig config = configOpt.get();
        ensureValidAccessToken(config);
        String cipher = ensureShopCipher(config);

        try {
            String path = "/customer_service/202309/conversations/" + conversationId.trim() + "/messages";
            long timestamp = Instant.now().getEpochSecond();

            Map<String, String> queryParams = new LinkedHashMap<>();
            queryParams.put("app_key", appKey);
            queryParams.put("shop_cipher", cipher);
            queryParams.put("timestamp", String.valueOf(timestamp));

            Map<String, Object> body = Map.of(
                    "type", messageType != null && !messageType.isBlank() ? messageType : "TEXT",
                    "content", content != null ? content : "");
            String bodyJson = objectMapper.writeValueAsString(body);

            String sign = generateSignature(path, queryParams, bodyJson);
            String url = String.format("%s%s?app_key=%s&shop_cipher=%s&timestamp=%d&sign=%s",
                    apiBaseUrl, path, appKey, cipher, timestamp, sign);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("x-tts-access-token", config.getAccessToken())
                    .timeout(Duration.ofSeconds(15))
                    .POST(HttpRequest.BodyPublishers.ofString(bodyJson))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode root = objectMapper.readTree(response.body());
            int code = root.path("code").asInt(-1);

            if (isTokenExpiredError(code, root.path("message").asText()) && refreshAccessToken(config)) {
                timestamp = Instant.now().getEpochSecond();
                queryParams.put("timestamp", String.valueOf(timestamp));
                sign = generateSignature(path, queryParams, bodyJson);
                url = String.format("%s%s?app_key=%s&shop_cipher=%s&timestamp=%d&sign=%s",
                        apiBaseUrl, path, appKey, cipher, timestamp, sign);
                request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Content-Type", "application/json")
                        .header("Accept", "application/json")
                        .header("x-tts-access-token", config.getAccessToken())
                        .timeout(Duration.ofSeconds(15))
                        .POST(HttpRequest.BodyPublishers.ofString(bodyJson))
                        .build();
                response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                root = objectMapper.readTree(response.body());
                code = root.path("code").asInt(-1);
            }

            if (code != 0) {
                return Map.of("success", false, "message", root.path("message").asText("Gagal mengirim pesan"));
            }

            return Map.of("success", true, "message", "Pesan berhasil dikirim ke pembeli TikTok Shop!");
        } catch (Exception e) {
            log.error("Failed to send message to TikTok: {}", e.getMessage(), e);
            return Map.of("success", false, "message", e.getMessage());
        }
    }
}

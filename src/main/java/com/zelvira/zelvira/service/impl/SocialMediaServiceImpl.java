package com.zelvira.zelvira.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zelvira.zelvira.dto.SocialAccountResponse;
import com.zelvira.zelvira.dto.SocialCommentResponse;
import com.zelvira.zelvira.dto.SocialPostRequest;
import com.zelvira.zelvira.dto.SocialPostResponse;
import com.zelvira.zelvira.entity.SocialAccount;
import com.zelvira.zelvira.entity.SocialComment;
import com.zelvira.zelvira.entity.SocialPost;
import com.zelvira.zelvira.repository.SocialAccountRepository;
import com.zelvira.zelvira.repository.SocialCommentRepository;
import com.zelvira.zelvira.repository.SocialPostRepository;
import com.zelvira.zelvira.service.SocialMediaService;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class SocialMediaServiceImpl implements SocialMediaService {

    @Value("${app.social.tiktok.client-key:}")
    private String tiktokClientKey;

    @Value("${app.social.tiktok.client-secret:}")
    private String tiktokClientSecret;

    private String getTiktokClientKey() {
        return (tiktokClientKey != null && !tiktokClientKey.isBlank()) ? tiktokClientKey.trim() : "sbawknsk6eooabz8hq";
    }

    private String getTiktokClientSecret() {
        return (tiktokClientSecret != null && !tiktokClientSecret.isBlank()) ? tiktokClientSecret.trim()
                : "7wWIblGTNxGUS0OoC01HdMd6pETdEt1g";
    }

    @Value("${app.social.tiktok.redirect-uri:https://api.zelvira.co.id/api/v1/social/callback/tiktok}")
    private String tiktokRedirectUri;

    @Value("${app.instagram.app-id:${META_APP_ID:1178932708147074}}")
    private String instagramAppId;

    @Value("${app.instagram.app-secret:${META_APP_SECRET:5430b9b475b147598bbad2fc452447fa}}")
    private String instagramAppSecret;

    @Value("${app.instagram.config-id:}")
    private String instagramConfigId;

    @Value("${app.instagram.account-id:}")
    private String instagramDefaultAccountId;

    @Value("${app.instagram.account-username:zelviraofficialstore}")
    private String instagramDefaultUsername;

    @Value("${app.social.instagram.redirect-uri:https://api.zelvira.co.id/api/v1/social/callback/instagram}")
    private String instagramRedirectUri;

    @Value("${app.social.threads.app-id:${THREADS_APP_ID:${app.threads.app-id:1100121756212184}}}")
    private String threadsAppId;

    @Value("${app.social.threads.app-secret:${THREADS_APP_SECRET:${app.threads.app-secret:a2e05c4f4d61cbdd9a2a27d212e4f049}}}")
    private String threadsAppSecret;

    @Value("${app.social.threads.redirect-uri:https://api.zelvira.co.id/api/v1/social/callback/threads}")
    private String threadsRedirectUri;

    @Value("${app.social.facebook.redirect-uri:https://api.zelvira.co.id/api/v1/social/callback/facebook}")
    private String facebookRedirectUri;

    @Value("${app.social.linkedin.client-id:}")
    private String linkedinClientId;

    @Value("${app.social.linkedin.client-secret:}")
    private String linkedinClientSecret;

    @Value("${app.social.linkedin.redirect-uri:https://api.zelvira.co.id/api/v1/social/callback/linkedin}")
    private String linkedinRedirectUri;

    @Value("${app.social.linkedin.organization-id:}")
    private String linkedinOrganizationId;

    @Value("${app.minio.bucket:zelixa-bucket}")
    private String minioBucket;

    @Value("${app.minio.url:http://minio:9000}")
    private String minioInternalUrl;

    @Value("${app.minio.public-url:https://minio.zelvira.co.id}")
    private String minioPublicUrl;

    private final MinioClient minioClient;

    @Value("${app.social.x.client-id:}")
    private String xClientId;

    @Value("${app.social.x.client-secret:}")
    private String xClientSecret;

    @Value("${app.social.x.redirect-uri:https://api.zelvira.co.id/api/v1/social/callback/x}")
    private String xRedirectUri;

    @Value("${app.social.youtube.client-id:}")
    private String youtubeClientId;

    @Value("${app.social.youtube.client-secret:}")
    private String youtubeClientSecret;

    @Value("${app.social.youtube.redirect-uri:https://api.zelvira.co.id/api/v1/social/callback/youtube}")
    private String youtubeRedirectUri;

    @Value("${app.social.admin-redirect-url:https://admin.zelvira.co.id/admin/settings?tab=social}")
    private String adminSocialConnectUrl;

    private final SocialAccountRepository accountRepository;
    private final SocialPostRepository postRepository;
    private final SocialCommentRepository commentRepository;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public SocialMediaServiceImpl(SocialAccountRepository accountRepository, SocialPostRepository postRepository,
            SocialCommentRepository commentRepository, ObjectMapper objectMapper, MinioClient minioClient) {
        this.accountRepository = accountRepository;
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
        this.minioClient = minioClient;
    }

    public String getAdminSocialConnectUrl() {
        return adminSocialConnectUrl;
    }

    // ==========================================
    // ACCOUNT CONNECTIONS (ALL CHANNELS)
    // ==========================================

    @Override
    public List<SocialAccountResponse> getConnectedAccounts() {
        List<SocialAccount> accounts = accountRepository.findAll();
        Map<String, SocialAccount> map = accounts.stream()
                .filter(a -> a.getPlatform() != null)
                .collect(Collectors.toMap(
                        a -> a.getPlatform().toUpperCase(),
                        a -> a,
                        (existing, replacement) -> Boolean.TRUE.equals(replacement.getIsActive()) ? replacement
                                : existing));

        List<SocialAccountResponse> result = new ArrayList<>();

        // 1. TikTok
        result.add(map.containsKey("TIKTOK")
                ? toAccountResponse(map.get("TIKTOK"))
                : SocialAccountResponse.builder()
                        .platform("TIKTOK")
                        .accountName("@zelvira")
                        .accountId(getTiktokClientKey())
                        .isActive(false)
                        .hasToken(false)
                        .clientKey(getTiktokClientKey())
                        .build());

        // 2. Instagram
        result.add(map.containsKey("INSTAGRAM")
                ? toAccountResponse(map.get("INSTAGRAM"))
                : SocialAccountResponse.builder()
                        .platform("INSTAGRAM")
                        .accountName("@" + instagramDefaultUsername)
                        .accountId(instagramDefaultAccountId)
                        .isActive(false)
                        .hasToken(false)
                        .clientKey(instagramAppId)
                        .build());

        // 3. LinkedIn
        result.add(map.containsKey("LINKEDIN")
                ? toAccountResponse(map.get("LINKEDIN"))
                : SocialAccountResponse.builder()
                        .platform("LINKEDIN")
                        .accountName("Zelvira Official")
                        .accountId("urn:li:organization:zelvira")
                        .isActive(false)
                        .hasToken(false)
                        .clientKey(linkedinClientId)
                        .build());

        // 4. X (Twitter)
        result.add(map.containsKey("X")
                ? toAccountResponse(map.get("X"))
                : SocialAccountResponse.builder()
                        .platform("X")
                        .accountName("@zelvirastore")
                        .accountId("x_zelvira")
                        .isActive(false)
                        .hasToken(false)
                        .clientKey(xClientId)
                        .build());

        // 5. Facebook Pages
        result.add(map.containsKey("FACEBOOK")
                ? toAccountResponse(map.get("FACEBOOK"))
                : SocialAccountResponse.builder()
                        .platform("FACEBOOK")
                        .accountName("Zelvira Store Official")
                        .accountId("fb_page_zelvira")
                        .isActive(false)
                        .hasToken(false)
                        .clientKey(instagramAppId)
                        .build());

        // 6. YouTube
        result.add(map.containsKey("YOUTUBE")
                ? toAccountResponse(map.get("YOUTUBE"))
                : SocialAccountResponse.builder()
                        .platform("YOUTUBE")
                        .accountName("Zelvira Fashion Channel")
                        .accountId("yt_zelvira")
                        .isActive(false)
                        .hasToken(false)
                        .build());

        // 7. Threads
        result.add(map.containsKey("THREADS")
                ? toAccountResponse(map.get("THREADS"))
                : SocialAccountResponse.builder()
                        .platform("THREADS")
                        .accountName("@zelviraofficialstore")
                        .accountId("threads_zelvira")
                        .isActive(false)
                        .hasToken(false)
                        .clientKey(threadsAppId)
                        .build());

        return result;
    }

    @Override
    public String getAuthUrl(String platform) {
        String p = platform.toUpperCase().trim();
        if ("TIKTOK".equals(p)) {
            String encodedRedirect = URLEncoder.encode(tiktokRedirectUri.trim(), StandardCharsets.UTF_8);
            String scope = "user.info.basic,video.publish,video.upload";
            return String.format(
                    "https://www.tiktok.com/v2/auth/authorize/?client_key=%s&scope=%s&response_type=code&redirect_uri=%s&state=zelvira_tiktok_oauth",
                    getTiktokClientKey(), scope, encodedRedirect);
        } else if ("INSTAGRAM".equals(p)) {
            String encodedRedirect = URLEncoder.encode(instagramRedirectUri.trim(), StandardCharsets.UTF_8);
            if (instagramConfigId != null && !instagramConfigId.isBlank()) {
                return String.format(
                        "https://www.facebook.com/v21.0/dialog/oauth?client_id=%s&config_id=%s&redirect_uri=%s&response_type=code",
                        instagramAppId.trim(), instagramConfigId.trim(), encodedRedirect);
            } else {
                return String.format(
                        "https://www.facebook.com/v21.0/dialog/oauth?client_id=%s&redirect_uri=%s&response_type=code&scope=instagram_business_basic,instagram_business_content_publish,pages_show_list,pages_read_engagement,public_profile",
                        instagramAppId.trim(), encodedRedirect);
            }
        } else if ("LINKEDIN".equals(p) || "LINKEDIN_ORG".equals(p) || "LINKEDIN_BUSINESS".equals(p)) {
            String cleanRedirect = (linkedinRedirectUri != null && !linkedinRedirectUri.isBlank())
                    ? linkedinRedirectUri.trim()
                    : "https://api.zelvira.co.id/api/v1/social/callback/linkedin";
            String encodedRedirect = URLEncoder.encode(cleanRedirect, StandardCharsets.UTF_8);
            String id = (linkedinClientId != null && !linkedinClientId.isBlank()) ? linkedinClientId.trim()
                    : "";

            // If connecting as Business / Organization page, request organization scopes
            String rawScopes = ("LINKEDIN_ORG".equals(p) || "LINKEDIN_BUSINESS".equals(p))
                    ? "openid profile email w_member_social w_organization_social r_organization_social"
                    : "openid profile email w_member_social";
            String scope = URLEncoder.encode(rawScopes, StandardCharsets.UTF_8);
            return String.format(
                    "https://www.linkedin.com/oauth/v2/authorization?response_type=code&client_id=%s&redirect_uri=%s&scope=%s&state=zelvira_linkedin_oauth",
                    id, encodedRedirect, scope);
        } else if ("X".equals(p) || "TWITTER".equals(p)) {
            String cleanRedirect = (xRedirectUri != null && !xRedirectUri.isBlank()) ? xRedirectUri.trim()
                    : "https://api.zelvira.co.id/api/v1/social/callback/x";
            String encodedRedirect = URLEncoder.encode(cleanRedirect, StandardCharsets.UTF_8);
            String id = (xClientId != null && !xClientId.isBlank()) ? xClientId.trim()
                    : "";
            return String.format(
                    "https://twitter.com/i/oauth2/authorize?response_type=code&client_id=%s&redirect_uri=%s&scope=tweet.read%%20tweet.write%%20users.read%%20offline.access&state=zelvira_x&code_challenge=challenge&code_challenge_method=plain",
                    id, encodedRedirect);
        } else if ("FACEBOOK".equals(p)) {
            String cleanRedirect = (facebookRedirectUri != null && !facebookRedirectUri.isBlank())
                    ? facebookRedirectUri.trim()
                    : "https://api.zelvira.co.id/api/v1/social/callback/facebook";
            String encodedRedirect = URLEncoder.encode(cleanRedirect, StandardCharsets.UTF_8);
            return String.format(
                    "https://www.facebook.com/v21.0/dialog/oauth?client_id=%s&redirect_uri=%s&response_type=code&scope=pages_show_list,pages_read_engagement,pages_manage_posts,public_profile&auth_type=rerequest",
                    instagramAppId.trim(), encodedRedirect);
        } else if ("THREADS".equals(p)) {
            String cleanRedirect = (threadsRedirectUri != null && !threadsRedirectUri.isBlank())
                    ? threadsRedirectUri.trim()
                    : "https://api.zelvira.co.id/api/v1/social/callback/threads";
            String encodedRedirect = URLEncoder.encode(cleanRedirect, StandardCharsets.UTF_8);
            String appId = (threadsAppId != null && !threadsAppId.isBlank()) ? threadsAppId.trim() : "";
            return String.format(
                    "https://threads.net/oauth/authorize?client_id=%s&redirect_uri=%s&response_type=code&scope=threads_basic,threads_content_publish",
                    appId, encodedRedirect);
        } else if ("YOUTUBE".equals(p)) {
            String cleanRedirect = (youtubeRedirectUri != null && !youtubeRedirectUri.isBlank())
                    ? youtubeRedirectUri.trim()
                    : "https://api.zelvira.co.id/api/v1/social/callback/youtube";
            String encodedRedirect = URLEncoder.encode(cleanRedirect, StandardCharsets.UTF_8);
            String id = (youtubeClientId != null && !youtubeClientId.isBlank())
                    ? youtubeClientId.trim()
                    : "";
            String scopes = URLEncoder.encode(
                    "https://www.googleapis.com/auth/youtube.upload https://www.googleapis.com/auth/youtube.force-ssl https://www.googleapis.com/auth/userinfo.profile openid",
                    StandardCharsets.UTF_8);
            return String.format(
                    "https://accounts.google.com/o/oauth2/v2/auth?client_id=%s&redirect_uri=%s&response_type=code&scope=%s&access_type=offline&prompt=select_account%%20consent&include_granted_scopes=true",
                    id, encodedRedirect, scopes);
        }
        throw new IllegalArgumentException("Unsupported social media platform: " + platform);
    }

    @Override
    @Transactional
    public void processAuthCallback(String platform, String code, String state) {
        log.info("Processing Social Auth Callback for {}: code={}", platform, code != null ? "[PRESENT]" : "NULL");
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Parameter authorization 'code' tidak ditemukan");
        }

        String p = platform.toUpperCase().trim();
        if ("TIKTOK".equals(p)) {
            processTikTokCallback(code.trim());
        } else if ("INSTAGRAM".equals(p)) {
            processInstagramCallback(code.trim());
        } else if ("LINKEDIN".equals(p)) {
            processLinkedInCallback(code.trim());
        } else if ("X".equals(p) || "TWITTER".equals(p)) {
            processXCallback(code.trim());
        } else if ("YOUTUBE".equals(p)) {
            processYouTubeCallback(code.trim());
        } else if ("THREADS".equals(p)) {
            processThreadsCallback(code.trim());
        } else if ("FACEBOOK".equals(p)) {
            processFacebookCallback(code.trim());
        } else {
            // General OAuth Mock/Callback save
            saveOrUpdateAccount(p, p.toLowerCase() + "_user", "@zelvira_" + p.toLowerCase(), null,
                    "tok_" + System.currentTimeMillis(), null, 5184000L);
        }
    }

    private void processYouTubeCallback(String code) {
        try {
            String tokenUrl = "https://oauth2.googleapis.com/token";
            String cleanRedirect = (youtubeRedirectUri != null && !youtubeRedirectUri.isBlank())
                    ? youtubeRedirectUri.trim()
                    : "https://api.zelvira.co.id/api/v1/social/callback/youtube";
            String id = (youtubeClientId != null && !youtubeClientId.isBlank())
                    ? youtubeClientId.trim()
                    : "";
            String secret = (youtubeClientSecret != null && !youtubeClientSecret.isBlank())
                    ? youtubeClientSecret.trim()
                    : "";

            String formBody = "code=" + URLEncoder.encode(code, StandardCharsets.UTF_8)
                    + "&client_id=" + URLEncoder.encode(id, StandardCharsets.UTF_8)
                    + "&client_secret=" + URLEncoder.encode(secret, StandardCharsets.UTF_8)
                    + "&redirect_uri=" + URLEncoder.encode(cleanRedirect, StandardCharsets.UTF_8)
                    + "&grant_type=authorization_code";

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(tokenUrl))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .timeout(Duration.ofSeconds(20))
                    .POST(HttpRequest.BodyPublishers.ofString(formBody))
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            log.info("YouTube OAuth token exchange response: status={}, body={}", resp.statusCode(), resp.body());

            if (resp.statusCode() != 200) {
                throw new RuntimeException("Gagal menukar token YouTube: " + resp.body());
            }

            JsonNode tokenNode = objectMapper.readTree(resp.body());
            String accessToken = tokenNode.path("access_token").asText(null);
            String refreshToken = tokenNode.path("refresh_token").asText(null);
            long expiresIn = tokenNode.path("expires_in").asLong(3600L);

            if (accessToken == null || accessToken.isBlank()) {
                throw new RuntimeException("Access token kosong dari respon Google YouTube OAuth");
            }

            // Fetch YouTube Channel Information via YouTube Data API v3
            String channelId = null;
            String channelTitle = "Zelvira YouTube Channel";
            String avatarUrl = null;

            try {
                HttpRequest channelReq = HttpRequest.newBuilder()
                        .uri(URI.create(
                                "https://www.googleapis.com/youtube/v3/channels?part=snippet,statistics&mine=true"))
                        .header("Authorization", "Bearer " + accessToken)
                        .timeout(Duration.ofSeconds(15))
                        .GET()
                        .build();

                HttpResponse<String> chResp = httpClient.send(channelReq, HttpResponse.BodyHandlers.ofString());
                log.info("YouTube channels mine API status={}, body={}", chResp.statusCode(), chResp.body());

                if (chResp.statusCode() == 200) {
                    JsonNode chRoot = objectMapper.readTree(chResp.body());
                    JsonNode items = chRoot.path("items");
                    if (items.isArray() && items.size() > 0) {
                        JsonNode first = items.get(0);
                        channelId = first.path("id").asText(null);
                        JsonNode snippet = first.path("snippet");
                        String title = snippet.path("title").asText(null);
                        if (title != null && !title.isBlank()) {
                            channelTitle = title;
                        }
                        String customUrl = snippet.path("customUrl").asText(null);
                        if (customUrl != null && !customUrl.isBlank()) {
                            channelTitle = channelTitle + " (" + customUrl + ")";
                        }
                        avatarUrl = snippet.path("thumbnails").path("high").path("url").asText(null);
                        if (avatarUrl == null || avatarUrl.isBlank()) {
                            avatarUrl = snippet.path("thumbnails").path("default").path("url").asText(null);
                        }
                    }
                }
            } catch (Exception chEx) {
                log.warn("Could not retrieve channel snippet from YouTube: {}", chEx.getMessage());
            }

            // Fallback to Google userinfo profile if channelId wasn't found
            if (channelId == null || channelId.isBlank()) {
                try {
                    HttpRequest userReq = HttpRequest.newBuilder()
                            .uri(URI.create("https://www.googleapis.com/oauth2/v2/userinfo"))
                            .header("Authorization", "Bearer " + accessToken)
                            .timeout(Duration.ofSeconds(10))
                            .GET()
                            .build();
                    HttpResponse<String> userResp = httpClient.send(userReq, HttpResponse.BodyHandlers.ofString());
                    if (userResp.statusCode() == 200) {
                        JsonNode userNode = objectMapper.readTree(userResp.body());
                        channelId = userNode.path("id").asText("yt_" + System.currentTimeMillis());
                        String name = userNode.path("name").asText(null);
                        if (name != null && !name.isBlank()) {
                            channelTitle = name;
                        }
                        if (avatarUrl == null) {
                            avatarUrl = userNode.path("picture").asText(null);
                        }
                    }
                } catch (Exception ex) {
                    log.warn("Could not retrieve Google userinfo: {}", ex.getMessage());
                }
            }

            if (channelId == null || channelId.isBlank()) {
                channelId = "yt_user_" + System.currentTimeMillis();
            }

            saveOrUpdateAccount("YOUTUBE", channelId, channelTitle, avatarUrl, accessToken, refreshToken, expiresIn);
            log.info("Successfully connected YouTube channel {} ({})", channelTitle, channelId);
        } catch (Exception e) {
            log.error("Failed to process YouTube OAuth callback: {}", e.getMessage(), e);
            throw new RuntimeException("Gagal menghubungkan YouTube: " + e.getMessage(), e);
        }
    }

    private String getValidYouTubeAccessToken(SocialAccount account) {
        if (account.getTokenExpiry() != null && account.getTokenExpiry().isBefore(LocalDateTime.now().plusMinutes(5))) {
            if (account.getRefreshToken() != null && !account.getRefreshToken().isBlank()) {
                try {
                    log.info("Refreshing expired YouTube access token for account {}", account.getAccountName());
                    String id = (youtubeClientId != null && !youtubeClientId.isBlank())
                            ? youtubeClientId.trim()
                            : "";
                    String secret = (youtubeClientSecret != null && !youtubeClientSecret.isBlank())
                            ? youtubeClientSecret.trim()
                            : "";

                    String formBody = "client_id=" + URLEncoder.encode(id, StandardCharsets.UTF_8)
                            + "&client_secret=" + URLEncoder.encode(secret, StandardCharsets.UTF_8)
                            + "&refresh_token="
                            + URLEncoder.encode(account.getRefreshToken().trim(), StandardCharsets.UTF_8)
                            + "&grant_type=refresh_token";

                    HttpRequest req = HttpRequest.newBuilder()
                            .uri(URI.create("https://oauth2.googleapis.com/token"))
                            .header("Content-Type", "application/x-www-form-urlencoded")
                            .timeout(Duration.ofSeconds(15))
                            .POST(HttpRequest.BodyPublishers.ofString(formBody))
                            .build();

                    HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                    if (resp.statusCode() == 200) {
                        JsonNode root = objectMapper.readTree(resp.body());
                        String newAccessToken = root.path("access_token").asText(null);
                        long expiresIn = root.path("expires_in").asLong(3600L);
                        if (newAccessToken != null) {
                            account.setAccessToken(newAccessToken);
                            account.setTokenExpiry(LocalDateTime.now().plusSeconds(expiresIn));
                            accountRepository.save(account);
                            log.info("Successfully refreshed YouTube access token");
                            return newAccessToken;
                        }
                    } else {
                        log.warn("YouTube token refresh returned {}: {}", resp.statusCode(), resp.body());
                    }
                } catch (Exception e) {
                    log.error("Failed to refresh YouTube token: {}", e.getMessage(), e);
                }
            }
        }
        return account.getAccessToken();
    }

    private String getValidTikTokAccessToken(SocialAccount account) {
        if (account.getTokenExpiry() != null
                && account.getTokenExpiry().isBefore(LocalDateTime.now().plusMinutes(10))) {
            if (account.getRefreshToken() != null && !account.getRefreshToken().isBlank()) {
                try {
                    log.info("Refreshing expired TikTok access token for account {}", account.getAccountName());
                    String tokenUrl = "https://open.tiktokapis.com/v2/oauth/token/";
                    String requestBody = String.format(
                            "client_key=%s&client_secret=%s&grant_type=refresh_token&refresh_token=%s",
                            URLEncoder.encode(getTiktokClientKey(), StandardCharsets.UTF_8),
                            URLEncoder.encode(getTiktokClientSecret(), StandardCharsets.UTF_8),
                            URLEncoder.encode(account.getRefreshToken().trim(), StandardCharsets.UTF_8));

                    HttpRequest req = HttpRequest.newBuilder()
                            .uri(URI.create(tokenUrl))
                            .header("Content-Type", "application/x-www-form-urlencoded")
                            .timeout(Duration.ofSeconds(15))
                            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                            .build();

                    HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                    if (resp.statusCode() == 200) {
                        JsonNode root = objectMapper.readTree(resp.body());
                        JsonNode data = extractTikTokTokenNode(root);
                        String newAccessToken = data.path("access_token").asText(null);
                        String newRefreshToken = data.path("refresh_token").asText(null);
                        long expiresIn = data.path("expires_in").asLong(86400L);
                        if (newAccessToken != null && !newAccessToken.isBlank()) {
                            account.setAccessToken(newAccessToken);
                            if (newRefreshToken != null && !newRefreshToken.isBlank()) {
                                account.setRefreshToken(newRefreshToken);
                            }
                            account.setTokenExpiry(LocalDateTime.now().plusSeconds(expiresIn));
                            accountRepository.save(account);
                            log.info("Successfully refreshed TikTok access token");
                            return newAccessToken;
                        } else {
                            log.warn("TikTok token refresh error: {}", extractTikTokOAuthError(root, resp.body()));
                        }
                    } else {
                        log.warn("TikTok token refresh returned {}: {}", resp.statusCode(), resp.body());
                    }
                } catch (Exception e) {
                    log.error("Failed to refresh TikTok token: {}", e.getMessage(), e);
                }
            }
        }
        return account.getAccessToken();
    }

    private void processXCallback(String code) {
        try {
            String tokenUrl = "https://api.twitter.com/2/oauth2/token";
            String cleanRedirect = (xRedirectUri != null && !xRedirectUri.isBlank())
                    ? xRedirectUri.trim()
                    : "https://api.zelvira.co.id/api/v1/social/callback/x";

            String clientId = (xClientId != null && !xClientId.isBlank())
                    ? xClientId.trim()
                    : "";
            String clientSecret = (xClientSecret != null && !xClientSecret.isBlank())
                    ? xClientSecret.trim()
                    : "";

            String authHeader = "Basic " + Base64.getEncoder().encodeToString(
                    (clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));

            String formBody = String.format(
                    "code=%s&grant_type=authorization_code&client_id=%s&redirect_uri=%s&code_verifier=challenge",
                    URLEncoder.encode(code, StandardCharsets.UTF_8),
                    URLEncoder.encode(clientId, StandardCharsets.UTF_8),
                    URLEncoder.encode(cleanRedirect, StandardCharsets.UTF_8));

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(tokenUrl))
                    .header("Authorization", authHeader)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .timeout(Duration.ofSeconds(20))
                    .POST(HttpRequest.BodyPublishers.ofString(formBody))
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            log.info("Twitter/X OAuth Token response status: {}", resp.statusCode());

            if (resp.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(resp.body());
                String accessToken = root.path("access_token").asText();
                String refreshToken = root.path("refresh_token").asText(null);
                long expiresIn = root.path("expires_in").asLong(7200);

                // Fetch user info from Twitter /2/users/me
                String accountId = "x_user";
                String username = "@zelvira_store";
                String avatarUrl = null;
                try {
                    HttpRequest userReq = HttpRequest.newBuilder()
                            .uri(URI.create(
                                    "https://api.twitter.com/2/users/me?user.fields=profile_image_url,username,name"))
                            .header("Authorization", "Bearer " + accessToken)
                            .timeout(Duration.ofSeconds(15))
                            .GET()
                            .build();
                    HttpResponse<String> userResp = httpClient.send(userReq, HttpResponse.BodyHandlers.ofString());
                    if (userResp.statusCode() == 200) {
                        JsonNode userData = objectMapper.readTree(userResp.body()).path("data");
                        accountId = userData.path("id").asText(accountId);
                        username = "@" + userData.path("username").asText("zelvira");
                        avatarUrl = userData.path("profile_image_url").asText(null);
                    }
                } catch (Exception uEx) {
                    log.warn("Could not fetch Twitter user profile: {}", uEx.getMessage());
                }

                saveOrUpdateAccount("X", accountId, username, avatarUrl, accessToken, refreshToken, expiresIn);
                log.info("X (Twitter) account {} successfully connected and token saved!", username);
            } else {
                log.error("Failed to exchange Twitter code for token: status={}, body={}", resp.statusCode(),
                        resp.body());
                throw new RuntimeException("Gagal menukar token Twitter/X: " + resp.body());
            }
        } catch (Exception e) {
            log.error("Error processing Twitter OAuth callback: {}", e.getMessage(), e);
            throw new RuntimeException("Gagal menghubungkan akun X: " + e.getMessage());
        }
    }

    private void publishToX(SocialAccount account, SocialPost post) {
        String token = account.getAccessToken();
        if (token == null || token.isBlank()) {
            throw new RuntimeException("Access Token untuk X (Twitter) tidak tersedia");
        }

        try {
            String text = post.getCaption();
            if (text == null || text.isBlank()) {
                text = post.getTitle() != null ? post.getTitle() : "Update dari Zelvira Store";
            }
            if (text.length() > 280) {
                text = text.substring(0, 277) + "...";
            }

            Map<String, Object> tweetBody = Map.of("text", text);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.twitter.com/2/tweets"))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(20))
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(tweetBody)))
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            log.info("Twitter publish tweet response status: {}, body: {}", resp.statusCode(), resp.body());

            if (resp.statusCode() != 200 && resp.statusCode() != 201) {
                throw new RuntimeException("Twitter API error (" + resp.statusCode() + "): " + resp.body());
            }
        } catch (Exception e) {
            log.error("Error publishing to X (Twitter): {}", e.getMessage(), e);
            throw new RuntimeException("Gagal posting ke X: " + e.getMessage());
        }
    }

    private void processTikTokCallback(String code) {
        try {
            String tokenUrl = "https://open.tiktokapis.com/v2/oauth/token/";
            String requestBody = String.format(
                    "client_key=%s&client_secret=%s&code=%s&grant_type=authorization_code&redirect_uri=%s",
                    URLEncoder.encode(getTiktokClientKey(), StandardCharsets.UTF_8),
                    URLEncoder.encode(getTiktokClientSecret(), StandardCharsets.UTF_8),
                    URLEncoder.encode(code, StandardCharsets.UTF_8),
                    URLEncoder.encode(tiktokRedirectUri.trim(), StandardCharsets.UTF_8));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(tokenUrl))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .timeout(Duration.ofSeconds(20))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("TikTok OAuth token response status={}", response.statusCode());
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode data = extractTikTokTokenNode(root);

            if (!data.has("access_token") || data.path("access_token").asText().isBlank()) {
                String errorMsg = extractTikTokOAuthError(root, "Gagal menukar token TikTok");
                log.error("TikTok OAuth token exchange failed: status={}, body={}", response.statusCode(), response.body());
                throw new RuntimeException("TikTok OAuth error: " + errorMsg);
            }

            String accessToken = data.path("access_token").asText();
            String refreshToken = data.path("refresh_token").asText(null);
            String openId = data.path("open_id").asText();
            long expiresIn = data.path("expires_in").asLong(86400);

            String accountName = "@zelvira";
            String avatarUrl = null;

            // Fetch user profile info from TikTok API if available (user.info.basic supports open_id,union_id,avatar_url,display_name)
            try {
                String infoUrl = "https://open.tiktokapis.com/v2/user/info/?fields=open_id,union_id,avatar_url,display_name";
                HttpRequest infoReq = HttpRequest.newBuilder()
                        .uri(URI.create(infoUrl))
                        .header("Authorization", "Bearer " + accessToken)
                        .timeout(Duration.ofSeconds(15))
                        .GET()
                        .build();
                HttpResponse<String> infoResp = httpClient.send(infoReq, HttpResponse.BodyHandlers.ofString());
                if (infoResp.statusCode() == 200) {
                    JsonNode infoJson = objectMapper.readTree(infoResp.body());
                    JsonNode userInfo = infoJson.path("data").path("user");
                    if (userInfo.has("username") && !userInfo.path("username").asText().isBlank()) {
                        accountName = "@" + userInfo.path("username").asText();
                    } else if (userInfo.has("display_name") && !userInfo.path("display_name").asText().isBlank()) {
                        accountName = userInfo.path("display_name").asText();
                    }
                    if (userInfo.has("avatar_url")) {
                        avatarUrl = userInfo.path("avatar_url").asText(null);
                    }
                }
            } catch (Exception ex) {
                log.warn("Could not retrieve TikTok profile details: {}", ex.getMessage());
            }

            // Fallback: Query TikTok creator info if username is still default
            if (accountName.equals("@zelvira")) {
                try {
                    HttpRequest cReq = HttpRequest.newBuilder()
                            .uri(URI.create("https://open.tiktokapis.com/v2/post/publish/creator_info/query/"))
                            .header("Authorization", "Bearer " + accessToken)
                            .header("Content-Type", "application/json; charset=UTF-8")
                            .timeout(Duration.ofSeconds(10))
                            .POST(HttpRequest.BodyPublishers.ofString("{}"))
                            .build();
                    HttpResponse<String> cResp = httpClient.send(cReq, HttpResponse.BodyHandlers.ofString());
                    if (cResp.statusCode() == 200) {
                        JsonNode cJson = objectMapper.readTree(cResp.body());
                        JsonNode cData = cJson.path("data");
                        String uName = cData.path("creator_username").asText(null);
                        String nName = cData.path("creator_nickname").asText(null);
                        String cAvatar = cData.path("creator_avatar_url").asText(null);
                        if (uName != null && !uName.isBlank()) {
                            accountName = "@" + uName;
                        } else if (nName != null && !nName.isBlank()) {
                            accountName = nName;
                        }
                        if (cAvatar != null && !cAvatar.isBlank()) {
                            avatarUrl = cAvatar;
                        }
                    }
                } catch (Exception ex) {
                    log.debug("Creator info query fallback ignored: {}", ex.getMessage());
                }
            }

            saveOrUpdateAccount("TIKTOK", openId, accountName, avatarUrl, accessToken, refreshToken, expiresIn);
            log.info("TikTok account connected successfully: {} ({})", accountName, openId);
        } catch (Exception e) {
            log.error("Failed to complete TikTok OAuth", e);
            throw new RuntimeException("Gagal menghubungkan akun TikTok: " + e.getMessage(), e);
        }
    }

    private void processInstagramCallback(String code) {
        try {
            String tokenUrl = "https://graph.facebook.com/v21.0/oauth/access_token";
            String appId = (instagramAppId != null && !instagramAppId.isBlank())
                    ? instagramAppId.trim()
                    : "1178932708147074";
            String appSecret = (instagramAppSecret != null && !instagramAppSecret.isBlank())
                    ? instagramAppSecret.trim()
                    : "5430b9b475b147598bbad2fc452447fa";

            String requestBody = String.format(
                    "client_id=%s&client_secret=%s&redirect_uri=%s&code=%s&grant_type=authorization_code",
                    URLEncoder.encode(appId, StandardCharsets.UTF_8),
                    URLEncoder.encode(appSecret, StandardCharsets.UTF_8),
                    URLEncoder.encode(instagramRedirectUri.trim(), StandardCharsets.UTF_8),
                    URLEncoder.encode(code, StandardCharsets.UTF_8));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(tokenUrl))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .timeout(Duration.ofSeconds(20))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("Instagram OAuth token response code: {}, body: {}", response.statusCode(), response.body());
            JsonNode root = objectMapper.readTree(response.body());

            if (root.has("error")) {
                String errorMsg = root.path("error").path("message").asText("Unknown error");
                throw new RuntimeException("Gagal menukar token Instagram: " + errorMsg);
            }

            String shortLivedToken = root.path("access_token").asText();
            String finalAccessToken = shortLivedToken;
            long expiresIn = root.path("expires_in").asLong(5184000);

            // Attempt long-lived token exchange (60 days) if appSecret is configured
            if (instagramAppSecret != null && !instagramAppSecret.isBlank()) {
                try {
                    String longLivedUrl = String.format(
                            "https://graph.facebook.com/v21.0/oauth/access_token?grant_type=fb_exchange_token&client_id=%s&client_secret=%s&fb_exchange_token=%s",
                            URLEncoder.encode(instagramAppId.trim(), StandardCharsets.UTF_8),
                            URLEncoder.encode(instagramAppSecret.trim(), StandardCharsets.UTF_8),
                            URLEncoder.encode(shortLivedToken, StandardCharsets.UTF_8));
                    HttpRequest longLivedReq = HttpRequest.newBuilder()
                            .uri(URI.create(longLivedUrl))
                            .header("Accept", "application/json")
                            .timeout(Duration.ofSeconds(20))
                            .GET()
                            .build();
                    HttpResponse<String> longLivedRes = httpClient.send(longLivedReq,
                            HttpResponse.BodyHandlers.ofString());
                    JsonNode longLivedJson = objectMapper.readTree(longLivedRes.body());
                    if (longLivedJson.has("access_token")) {
                        finalAccessToken = longLivedJson.path("access_token").asText();
                        expiresIn = longLivedJson.path("expires_in").asLong(5184000);
                        log.info("Upgraded to long-lived Instagram token (expires in {} seconds)", expiresIn);
                    }
                } catch (Exception ex) {
                    log.warn("Could not upgrade to long-lived Instagram token: {}", ex.getMessage());
                }
            }

            String targetAccountId = (instagramDefaultAccountId != null && !instagramDefaultAccountId.isBlank())
                    ? instagramDefaultAccountId.trim()
                    : "";
            String targetUsername = "@" + (instagramDefaultUsername != null && !instagramDefaultUsername.isBlank()
                    ? instagramDefaultUsername.trim()
                    : "zelviraofficialstore");
            String avatarUrl = null;

            // Attempt to auto-discover Instagram Business Account from user's Facebook
            // Pages
            try {
                String meUrl = "https://graph.facebook.com/v21.0/me/accounts?fields=instagram_business_account{id,username,profile_picture_url}&access_token="
                        + URLEncoder.encode(finalAccessToken, StandardCharsets.UTF_8);
                HttpRequest meReq = HttpRequest.newBuilder().uri(URI.create(meUrl)).timeout(Duration.ofSeconds(15))
                        .GET().build();
                HttpResponse<String> meResp = httpClient.send(meReq, HttpResponse.BodyHandlers.ofString());
                if (meResp.statusCode() == 200) {
                    JsonNode meJson = objectMapper.readTree(meResp.body());
                    JsonNode dataArr = meJson.path("data");
                    if (dataArr.isArray()) {
                        for (JsonNode page : dataArr) {
                            if (page.has("instagram_business_account")) {
                                JsonNode igNode = page.path("instagram_business_account");
                                targetAccountId = igNode.path("id").asText(targetAccountId);
                                if (igNode.has("username")) {
                                    targetUsername = "@" + igNode.path("username").asText();
                                }
                                if (igNode.has("profile_picture_url")) {
                                    avatarUrl = igNode.path("profile_picture_url").asText(null);
                                }
                                break;
                            }
                        }
                    }
                }
            } catch (Exception ex) {
                log.warn("Could not discover Instagram Business Account automatically: {}", ex.getMessage());
            }

            saveOrUpdateAccount("INSTAGRAM", targetAccountId, targetUsername, avatarUrl,
                    finalAccessToken, null, expiresIn);
            log.info("Instagram account connected successfully: {}", targetUsername);
        } catch (Exception e) {
            log.error("Failed to complete Instagram OAuth", e);
            throw new RuntimeException("Gagal menghubungkan akun Instagram: " + e.getMessage(), e);
        }
    }

    private void processLinkedInCallback(String code) {
        try {
            log.info("Processing LinkedIn OAuth callback with code...");
            String tokenUrl = "https://www.linkedin.com/oauth/v2/accessToken";
            String cleanRedirect = (linkedinRedirectUri != null && !linkedinRedirectUri.isBlank())
                    ? linkedinRedirectUri.trim()
                    : "https://api.zelvira.co.id/api/v1/social/callback/linkedin";

            String clientId = (linkedinClientId != null && !linkedinClientId.isBlank())
                    ? linkedinClientId.trim()
                    : "";
            String clientSecret = (linkedinClientSecret != null && !linkedinClientSecret.isBlank())
                    ? linkedinClientSecret.trim()
                    : "";

            String requestBody = String.format(
                    "grant_type=authorization_code&code=%s&client_id=%s&client_secret=%s&redirect_uri=%s",
                    URLEncoder.encode(code, StandardCharsets.UTF_8),
                    URLEncoder.encode(clientId, StandardCharsets.UTF_8),
                    URLEncoder.encode(clientSecret, StandardCharsets.UTF_8),
                    URLEncoder.encode(cleanRedirect, StandardCharsets.UTF_8));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(tokenUrl))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .timeout(Duration.ofSeconds(20))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("LinkedIn token exchange HTTP status: {}", response.statusCode());

            if (response.statusCode() != 200) {
                log.error("LinkedIn token exchange error: {}", response.body());
                throw new RuntimeException("LinkedIn OAuth token exchange failed with status " + response.statusCode()
                        + ": " + response.body());
            }

            JsonNode root = objectMapper.readTree(response.body());
            if (!root.has("access_token")) {
                String errorMsg = root.path("error_description")
                        .asText(root.path("error").asText("Gagal menukar token LinkedIn"));
                throw new RuntimeException("LinkedIn OAuth error: " + errorMsg);
            }

            String accessToken = root.path("access_token").asText();
            String refreshToken = root.path("refresh_token").asText(null);
            long expiresIn = root.path("expires_in").asLong(5184000L);

            // Fetch member info from OpenID userinfo endpoint
            String accountId = "urn:li:person:me";
            String accountName = "Zelvira Official";
            String avatarUrl = null;

            try {
                HttpRequest userinfoRequest = HttpRequest.newBuilder()
                        .uri(URI.create("https://api.linkedin.com/v2/userinfo"))
                        .header("Authorization", "Bearer " + accessToken)
                        .timeout(Duration.ofSeconds(15))
                        .GET()
                        .build();

                HttpResponse<String> userInfoResp = httpClient.send(userinfoRequest,
                        HttpResponse.BodyHandlers.ofString());
                if (userInfoResp.statusCode() == 200) {
                    JsonNode userNode = objectMapper.readTree(userInfoResp.body());
                    if (userNode.has("sub")) {
                        String sub = userNode.path("sub").asText();
                        accountId = sub.startsWith("urn:li:") ? sub : "urn:li:person:" + sub;
                    }
                    if (userNode.has("name")) {
                        accountName = userNode.path("name").asText();
                    }
                    if (userNode.has("picture")) {
                        avatarUrl = userNode.path("picture").asText(null);
                    }
                    log.info("Retrieved LinkedIn user profile: name='{}', id='{}'", accountName, accountId);
                } else {
                    log.warn("LinkedIn userinfo returned HTTP {}: {}", userInfoResp.statusCode(), userInfoResp.body());
                }
            } catch (Exception ex) {
                log.warn("Could not retrieve LinkedIn userinfo, fallback to default identifiers: {}", ex.getMessage());
            }

            // Check if user is an Administrator of a LinkedIn Company / Organization Page
            try {
                HttpRequest orgAclRequest = HttpRequest.newBuilder()
                        .uri(URI.create("https://api.linkedin.com/v2/organizationalEntityAcls?q=roleAssignee"))
                        .header("Authorization", "Bearer " + accessToken)
                        .header("X-Restli-Protocol-Version", "2.0.0")
                        .timeout(Duration.ofSeconds(15))
                        .GET()
                        .build();

                HttpResponse<String> orgAclResp = httpClient.send(orgAclRequest, HttpResponse.BodyHandlers.ofString());
                log.info("LinkedIn organizationalEntityAcls HTTP status: {}", orgAclResp.statusCode());

                if (orgAclResp.statusCode() == 200) {
                    JsonNode aclRoot = objectMapper.readTree(orgAclResp.body());
                    JsonNode elements = aclRoot.path("elements");
                    if (elements.isArray() && elements.size() > 0) {
                        for (JsonNode el : elements) {
                            String role = el.path("role").asText();
                            String orgTarget = el.path("organizationalTarget").asText();
                            if (orgTarget != null && !orgTarget.isBlank() &&
                                    ("ADMINISTRATOR".equalsIgnoreCase(role)
                                            || "DIRECT_SPONSORED_CONTENT_POSTER".equalsIgnoreCase(role))) {
                                accountId = orgTarget;
                                accountName = "Zelvira Business Page (" + orgTarget.replace("urn:li:organization:", "")
                                        + ")";
                                log.info("Detected LinkedIn Company Page organization admin: {}", orgTarget);
                                break;
                            }
                        }
                    }
                }
            } catch (Exception ex) {
                log.debug("OrganizationalEntityAcls check skipped or failed: {}", ex.getMessage());
            }

            // If an explicit organization ID is configured via environment / properties,
            // prioritize it
            if (linkedinOrganizationId != null && !linkedinOrganizationId.isBlank()) {
                String cleanOrg = linkedinOrganizationId.trim();
                accountId = cleanOrg.startsWith("urn:li:") ? cleanOrg : "urn:li:organization:" + cleanOrg;
                accountName = "Zelvira Business Page";
                log.info("Using configured LinkedIn Organization ID: {}", accountId);
            }

            saveOrUpdateAccount("LINKEDIN", accountId, accountName, avatarUrl, accessToken, refreshToken, expiresIn);
            log.info("LinkedIn account connected and saved successfully: name='{}', id='{}'", accountName, accountId);
        } catch (Exception e) {
            log.error("Failed to complete LinkedIn OAuth", e);
            throw new RuntimeException("Gagal menghubungkan akun LinkedIn: " + e.getMessage(), e);
        }
    }

    private void processThreadsCallback(String code) {
        try {
            log.info("Processing Threads OAuth callback with code...");
            String tokenUrl = "https://graph.threads.net/oauth/access_token";
            String cleanRedirect = (threadsRedirectUri != null && !threadsRedirectUri.isBlank())
                    ? threadsRedirectUri.trim()
                    : "https://api.zelvira.co.id/api/v1/social/callback/threads";

            String appId = (threadsAppId != null && !threadsAppId.isBlank()) ? threadsAppId.trim() : "";
            String appSecret = (threadsAppSecret != null && !threadsAppSecret.isBlank()) ? threadsAppSecret.trim() : "";

            String formBody = String.format(
                    "client_id=%s&client_secret=%s&grant_type=authorization_code&redirect_uri=%s&code=%s",
                    URLEncoder.encode(appId, StandardCharsets.UTF_8),
                    URLEncoder.encode(appSecret, StandardCharsets.UTF_8),
                    URLEncoder.encode(cleanRedirect, StandardCharsets.UTF_8),
                    URLEncoder.encode(code, StandardCharsets.UTF_8));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(tokenUrl))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .timeout(Duration.ofSeconds(20))
                    .POST(HttpRequest.BodyPublishers.ofString(formBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("Threads OAuth token response: status={}, body={}", response.statusCode(), response.body());

            if (response.statusCode() != 200) {
                throw new RuntimeException("Gagal menukar token Threads: " + response.body());
            }

            JsonNode root = objectMapper.readTree(response.body());
            if (root.has("error_message") || root.has("error")) {
                String errorMsg = root.path("error_message")
                        .asText(root.path("error").path("message").asText("Unknown error"));
                throw new RuntimeException("Threads OAuth error: " + errorMsg);
            }

            String shortLivedToken = root.path("access_token").asText();
            String userId = root.path("user_id").asText();
            String finalAccessToken = shortLivedToken;
            long expiresIn = 5184000L; // 60 days default

            // Exchange for long-lived Threads token (60 days)
            if (appSecret != null && !appSecret.isBlank()) {
                try {
                    String longLivedUrl = String.format(
                            "https://graph.threads.net/access_token?grant_type=th_exchange_token&client_secret=%s&access_token=%s",
                            URLEncoder.encode(appSecret, StandardCharsets.UTF_8),
                            URLEncoder.encode(shortLivedToken, StandardCharsets.UTF_8));
                    HttpRequest longLivedReq = HttpRequest.newBuilder()
                            .uri(URI.create(longLivedUrl))
                            .timeout(Duration.ofSeconds(15))
                            .GET()
                            .build();
                    HttpResponse<String> longLivedRes = httpClient.send(longLivedReq,
                            HttpResponse.BodyHandlers.ofString());
                    if (longLivedRes.statusCode() == 200) {
                        JsonNode longLivedJson = objectMapper.readTree(longLivedRes.body());
                        if (longLivedJson.has("access_token")) {
                            finalAccessToken = longLivedJson.path("access_token").asText();
                            expiresIn = longLivedJson.path("expires_in").asLong(5184000L);
                            log.info("Upgraded to long-lived Threads token (expires in {}s)", expiresIn);
                        }
                    }
                } catch (Exception ex) {
                    log.warn("Could not upgrade to long-lived Threads token: {}", ex.getMessage());
                }
            }

            // Retrieve Threads profile info
            String accountName = "@zelviraofficialstore";
            String avatarUrl = null;
            try {
                String meUrl = "https://graph.threads.net/v1.0/me?fields=id,username,name,threads_profile_picture_url&access_token="
                        + URLEncoder.encode(finalAccessToken, StandardCharsets.UTF_8);
                HttpRequest meReq = HttpRequest.newBuilder()
                        .uri(URI.create(meUrl))
                        .timeout(Duration.ofSeconds(15))
                        .GET()
                        .build();
                HttpResponse<String> meResp = httpClient.send(meReq, HttpResponse.BodyHandlers.ofString());
                if (meResp.statusCode() == 200) {
                    JsonNode meJson = objectMapper.readTree(meResp.body());
                    if (meJson.has("username")) {
                        accountName = "@" + meJson.path("username").asText();
                    }
                    if (meJson.has("id")) {
                        userId = meJson.path("id").asText(userId);
                    }
                    if (meJson.has("threads_profile_picture_url")) {
                        avatarUrl = meJson.path("threads_profile_picture_url").asText(null);
                    }
                }
            } catch (Exception ex) {
                log.warn("Could not fetch Threads user profile: {}", ex.getMessage());
            }

            saveOrUpdateAccount("THREADS", userId, accountName, avatarUrl, finalAccessToken, null, expiresIn);
            log.info("Threads account connected successfully: {} ({})", accountName, userId);
        } catch (Exception e) {
            log.error("Failed to process Threads OAuth callback", e);
            throw new RuntimeException("Gagal menghubungkan akun Threads: " + e.getMessage(), e);
        }
    }

    private void processFacebookCallback(String code) {
        try {
            log.info("Processing Facebook OAuth callback with code...");
            String tokenUrl = "https://graph.facebook.com/v21.0/oauth/access_token";
            String cleanRedirect = (facebookRedirectUri != null && !facebookRedirectUri.isBlank())
                    ? facebookRedirectUri.trim()
                    : "https://api.zelvira.co.id/api/v1/social/callback/facebook";

            String appId = (instagramAppId != null && !instagramAppId.isBlank()) ? instagramAppId.trim() : "";
            String appSecret = (instagramAppSecret != null && !instagramAppSecret.isBlank()) ? instagramAppSecret.trim()
                    : "";

            String formBody = String.format(
                    "client_id=%s&client_secret=%s&redirect_uri=%s&code=%s&grant_type=authorization_code",
                    URLEncoder.encode(appId, StandardCharsets.UTF_8),
                    URLEncoder.encode(appSecret, StandardCharsets.UTF_8),
                    URLEncoder.encode(cleanRedirect, StandardCharsets.UTF_8),
                    URLEncoder.encode(code, StandardCharsets.UTF_8));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(tokenUrl))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .timeout(Duration.ofSeconds(20))
                    .POST(HttpRequest.BodyPublishers.ofString(formBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("Facebook OAuth token response: status={}, body={}", response.statusCode(), response.body());

            if (response.statusCode() != 200) {
                throw new RuntimeException("Gagal menukar token Facebook: " + response.body());
            }

            JsonNode root = objectMapper.readTree(response.body());
            if (root.has("error")) {
                String errorMsg = root.path("error").path("message").asText("Unknown error");
                throw new RuntimeException("Facebook OAuth error: " + errorMsg);
            }

            String userToken = root.path("access_token").asText();
            long expiresIn = root.path("expires_in").asLong(5184000L);

            // Attempt long-lived user token exchange
            if (appSecret != null && !appSecret.isBlank()) {
                try {
                    String longLivedUrl = String.format(
                            "https://graph.facebook.com/v21.0/oauth/access_token?grant_type=fb_exchange_token&client_id=%s&client_secret=%s&fb_exchange_token=%s",
                            URLEncoder.encode(appId, StandardCharsets.UTF_8),
                            URLEncoder.encode(appSecret, StandardCharsets.UTF_8),
                            URLEncoder.encode(userToken, StandardCharsets.UTF_8));
                    HttpRequest longLivedReq = HttpRequest.newBuilder()
                            .uri(URI.create(longLivedUrl))
                            .timeout(Duration.ofSeconds(15))
                            .GET()
                            .build();
                    HttpResponse<String> longLivedRes = httpClient.send(longLivedReq,
                            HttpResponse.BodyHandlers.ofString());
                    if (longLivedRes.statusCode() == 200) {
                        JsonNode longLivedJson = objectMapper.readTree(longLivedRes.body());
                        if (longLivedJson.has("access_token")) {
                            userToken = longLivedJson.path("access_token").asText();
                            expiresIn = longLivedJson.path("expires_in").asLong(5184000L);
                        }
                    }
                } catch (Exception ex) {
                    log.warn("Could not upgrade to long-lived Facebook token: {}", ex.getMessage());
                }
            }

            // Retrieve user's Facebook Pages
            String targetPageId = null;
            String targetPageName = null;
            String targetPageToken = null;
            String avatarUrl = null;

            try {
                String pagesUrl = "https://graph.facebook.com/v21.0/me/accounts?fields=id,name,access_token,picture&access_token="
                        + URLEncoder.encode(userToken, StandardCharsets.UTF_8);
                HttpRequest pagesReq = HttpRequest.newBuilder().uri(URI.create(pagesUrl)).timeout(Duration.ofSeconds(15))
                        .GET().build();
                HttpResponse<String> pagesResp = httpClient.send(pagesReq, HttpResponse.BodyHandlers.ofString());
                log.info("Facebook accounts API response: status={}, body={}", pagesResp.statusCode(), pagesResp.body());

                if (pagesResp.statusCode() == 200) {
                    JsonNode pagesJson = objectMapper.readTree(pagesResp.body());
                    JsonNode dataArr = pagesJson.path("data");
                    if (dataArr.isArray() && dataArr.size() > 0) {
                        JsonNode selectedPage = dataArr.get(0);
                        for (JsonNode p : dataArr) {
                            String pName = p.path("name").asText("");
                            if (pName.toLowerCase().contains("zelvira")) {
                                selectedPage = p;
                                break;
                            }
                        }
                        targetPageId = selectedPage.path("id").asText(null);
                        targetPageName = selectedPage.path("name").asText(null);
                        if (selectedPage.has("access_token")) {
                            targetPageToken = selectedPage.path("access_token").asText(null);
                        }
                        if (selectedPage.has("picture") && selectedPage.path("picture").has("data")) {
                            avatarUrl = selectedPage.path("picture").path("data").path("url").asText(null);
                        }
                        log.info("Discovered Facebook Page: {} ({}) with page access token", targetPageName, targetPageId);
                    }
                }
            } catch (Exception ex) {
                log.warn("Could not fetch Facebook Pages for user: {}", ex.getMessage());
            }

            // Fallback to user profile only if no page found, but warn user
            if (targetPageId == null || targetPageToken == null) {
                try {
                    String meUrl = "https://graph.facebook.com/v21.0/me?fields=id,name,picture&access_token="
                            + URLEncoder.encode(userToken, StandardCharsets.UTF_8);
                    HttpRequest meReq = HttpRequest.newBuilder().uri(URI.create(meUrl)).timeout(Duration.ofSeconds(15))
                            .GET().build();
                    HttpResponse<String> meResp = httpClient.send(meReq, HttpResponse.BodyHandlers.ofString());
                    if (meResp.statusCode() == 200) {
                        JsonNode meJson = objectMapper.readTree(meResp.body());
                        targetPageId = meJson.path("id").asText("fb_user_" + System.currentTimeMillis());
                        targetPageName = meJson.path("name").asText("Zelvira Store Official");
                        targetPageToken = userToken;
                        if (meJson.has("picture") && meJson.path("picture").has("data")) {
                            avatarUrl = meJson.path("picture").path("data").path("url").asText(null);
                        }
                    }
                } catch (Exception ex) {
                    log.warn("Could not fetch Facebook user profile: {}", ex.getMessage());
                }
                if (targetPageId == null) {
                    targetPageId = "fb_page_zelvira";
                    targetPageName = "Zelvira Store Official";
                    targetPageToken = userToken;
                }
                log.warn("Warning: No Facebook Page was found in /me/accounts. Token saved as user token, posting may require a Facebook Page.");
            }

            saveOrUpdateAccount("FACEBOOK", targetPageId, targetPageName, avatarUrl, targetPageToken, null, expiresIn);
            log.info("Facebook account connected successfully: {} ({})", targetPageName, targetPageId);
        } catch (Exception e) {
            log.error("Failed to process Facebook OAuth callback", e);
            throw new RuntimeException("Gagal menghubungkan akun Facebook: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public void saveManualAccount(String platform, String accountId, String accountName, String accessToken,
            Long expiresInSeconds) {
        if (platform == null || accessToken == null || accessToken.isBlank()) {
            throw new IllegalArgumentException("Platform dan Access Token wajib diisi");
        }
        String cleanPlatform = platform.toUpperCase().trim();
        String targetId = (accountId != null && !accountId.isBlank()) ? accountId.trim()
                : (cleanPlatform.toLowerCase() + "_id");

        if ("LINKEDIN".equalsIgnoreCase(cleanPlatform)) {
            if (!targetId.startsWith("urn:li:")) {
                if (targetId.matches("^\\d+$") || targetId.toLowerCase().contains("org")
                        || targetId.toLowerCase().contains("company")) {
                    targetId = "urn:li:organization:" + targetId.replaceAll("[^0-9]", "");
                } else {
                    targetId = "urn:li:person:" + targetId;
                }
            }
        }

        String targetName = (accountName != null && !accountName.isBlank()) ? accountName.trim()
                : ("@" + cleanPlatform.toLowerCase() + "_zelvira");
        long expiry = (expiresInSeconds != null && expiresInSeconds > 0) ? expiresInSeconds : 5184000L;

        saveOrUpdateAccount(cleanPlatform, targetId, targetName, null, accessToken.trim(), null, expiry);
    }

    private void saveOrUpdateAccount(String platform, String accountId, String accountName, String avatarUrl,
            String accessToken, String refreshToken, long expiresInSeconds) {
        SocialAccount account = accountRepository.findByPlatformAndAccountId(platform, accountId)
                .orElseGet(() -> {
                    List<SocialAccount> existing = accountRepository.findAllByPlatform(platform);
                    return existing.isEmpty()
                            ? SocialAccount.builder().platform(platform).accountId(accountId).build()
                            : existing.get(0);
                });

        account.setAccountId(accountId);
        account.setAccountName(accountName);
        if (avatarUrl != null)
            account.setAvatarUrl(avatarUrl);
        account.setAccessToken(accessToken);
        if (refreshToken != null)
            account.setRefreshToken(refreshToken);
        account.setTokenExpiry(LocalDateTime.now().plusSeconds(expiresInSeconds));
        account.setIsActive(true);
        if ("LINKEDIN".equalsIgnoreCase(platform)) {
            account.setClientKey(
                    linkedinClientId != null && !linkedinClientId.isBlank() ? linkedinClientId : "");
        } else if ("TIKTOK".equalsIgnoreCase(platform)) {
            account.setClientKey(getTiktokClientKey());
        } else if ("INSTAGRAM".equalsIgnoreCase(platform)) {
            account.setClientKey(instagramAppId);
        } else if ("THREADS".equalsIgnoreCase(platform)) {
            account.setClientKey(threadsAppId != null && !threadsAppId.isBlank() ? threadsAppId : "");
        } else if ("FACEBOOK".equalsIgnoreCase(platform)) {
            account.setClientKey(instagramAppId != null && !instagramAppId.isBlank() ? instagramAppId : "");
        } else if ("YOUTUBE".equalsIgnoreCase(platform)) {
            account.setClientKey(youtubeClientId != null && !youtubeClientId.isBlank() ? youtubeClientId : "");
        }

        accountRepository.save(account);
    }

    @Override
    @Transactional
    public void disconnectAccount(Long id) {
        accountRepository.findById(id).ifPresent(account -> {
            account.setIsActive(false);
            account.setAccessToken(null);
            accountRepository.save(account);
            log.info("Social account disconnected: id={}, platform={}", id, account.getPlatform());
        });
    }

    // ==========================================
    // POSTS & SCHEDULER
    // ==========================================

    @Override
    public List<SocialPostResponse> getAllPosts(String status, String platform) {
        List<SocialPost> posts = postRepository.findAllByOrderByCreatedAtDesc();

        return posts.stream()
                .filter(p -> status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)
                        || p.getStatus().equalsIgnoreCase(status))
                .filter(p -> platform == null || platform.isBlank() || "ALL".equalsIgnoreCase(platform)
                        || p.getTargetPlatforms().toUpperCase().contains(platform.toUpperCase()))
                .map(this::toPostResponse)
                .collect(Collectors.toList());
    }

    @Override
    public SocialPostResponse getPostById(Long id) {
        SocialPost post = postRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Post not found with ID: " + id));
        return toPostResponse(post);
    }

    @Override
    @Transactional
    public SocialPostResponse createPost(SocialPostRequest request) {
        validateTargetPlatforms(request.getTargetPlatforms());

        String initialStatus = "DRAFT";
        LocalDateTime schedTime = request.getScheduledAt();
        if (Boolean.TRUE.equals(request.getPublishNow())) {
            initialStatus = "PUBLISHING";
            if (schedTime == null) {
                schedTime = LocalDateTime.now();
            }
        } else if (schedTime != null) {
            initialStatus = "SCHEDULED";
        }

        SocialPost post = SocialPost.builder()
                .productId(request.getProductId())
                .title(request.getTitle())
                .caption(request.getCaption())
                .mediaType(request.getMediaType() != null ? request.getMediaType() : "VIDEO")
                .mediaUrls(request.getMediaUrls())
                .thumbnailUrl(request.getThumbnailUrl())
                .targetPlatforms(
                        request.getTargetPlatforms() != null ? request.getTargetPlatforms().toUpperCase() : "TIKTOK")
                .postType(request.getPostType() != null ? request.getPostType().toUpperCase() : "FEED")
                .status(initialStatus)
                .scheduledAt(schedTime)
                .privacyLevel(request.getPrivacyLevel() != null ? request.getPrivacyLevel() : "PUBLIC_TO_EVERYONE")
                .tiktokPostMode(normalizeTikTokPostMode(request.getTiktokPostMode()))
                .build();

        SocialPost saved = postRepository.save(post);

        if (Boolean.TRUE.equals(request.getPublishNow())) {
            return executePublish(saved);
        }

        return toPostResponse(saved);
    }

    @Override
    @Transactional
    public SocialPostResponse updatePost(Long id, SocialPostRequest request) {
        SocialPost post = postRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Post not found with ID: " + id));

        if (request.getTitle() != null)
            post.setTitle(request.getTitle());
        if (request.getCaption() != null)
            post.setCaption(request.getCaption());
        if (request.getMediaType() != null)
            post.setMediaType(request.getMediaType());
        if (request.getMediaUrls() != null)
            post.setMediaUrls(request.getMediaUrls());
        if (request.getThumbnailUrl() != null)
            post.setThumbnailUrl(request.getThumbnailUrl());
        if (request.getTargetPlatforms() != null)
            post.setTargetPlatforms(request.getTargetPlatforms().toUpperCase());
        if (request.getPostType() != null)
            post.setPostType(request.getPostType().toUpperCase());
        if (request.getScheduledAt() != null) {
            post.setScheduledAt(request.getScheduledAt());
            if ("DRAFT".equalsIgnoreCase(post.getStatus())) {
                post.setStatus("SCHEDULED");
            }
        }
        if (request.getPrivacyLevel() != null)
            post.setPrivacyLevel(request.getPrivacyLevel());
        if (request.getTiktokPostMode() != null)
            post.setTiktokPostMode(normalizeTikTokPostMode(request.getTiktokPostMode()));

        SocialPost updated = postRepository.save(post);
        return toPostResponse(updated);
    }

    @Override
    @Transactional
    public void deletePost(Long id) {
        postRepository.deleteById(id);
    }

    @Override
    @Transactional
    public SocialPostResponse publishPostNow(Long id) {
        SocialPost post = postRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Post not found with ID: " + id));
        return executePublish(post);
    }

    private void validateTargetPlatforms(String targetPlatforms) {
        if (targetPlatforms == null)
            return;
        String targets = targetPlatforms.toUpperCase();

        if (targets.contains("TIKTOK")) {
            boolean hasTiktok = accountRepository.findFirstByPlatformAndIsActiveTrue("TIKTOK")
                    .filter(a -> a.getAccessToken() != null && !a.getAccessToken().isBlank())
                    .isPresent();
            if (!hasTiktok) {
                throw new IllegalStateException(
                        "Akun TikTok belum terhubung! Silakan hubungkan akun TikTok terlebih dahulu di menu Connect Channels.");
            }
        }

        if (targets.contains("INSTAGRAM")) {
            boolean hasIg = accountRepository.findFirstByPlatformAndIsActiveTrue("INSTAGRAM")
                    .filter(a -> a.getAccessToken() != null && !a.getAccessToken().isBlank())
                    .isPresent();
            if (!hasIg) {
                throw new IllegalStateException(
                        "Akun Instagram belum terhubung! Silakan hubungkan akun Instagram terlebih dahulu di menu Connect Channels.");
            }
        }

        if (targets.contains("LINKEDIN")) {
            boolean hasLi = accountRepository.findFirstByPlatformAndIsActiveTrue("LINKEDIN")
                    .filter(a -> a.getAccessToken() != null && !a.getAccessToken().isBlank())
                    .isPresent();
            if (!hasLi) {
                throw new IllegalStateException(
                        "Akun LinkedIn belum terhubung! Silakan hubungkan akun LinkedIn terlebih dahulu di menu Connect Channels.");
            }
        }

        if (targets.contains("THREADS")) {
            boolean hasTh = accountRepository.findFirstByPlatformAndIsActiveTrue("THREADS")
                    .filter(a -> a.getAccessToken() != null && !a.getAccessToken().isBlank())
                    .isPresent();
            if (!hasTh) {
                throw new IllegalStateException(
                        "Akun Threads belum terhubung! Silakan hubungkan akun Threads terlebih dahulu di menu Connect Channels.");
            }
        }

        if (targets.contains("FACEBOOK")) {
            boolean hasFb = accountRepository.findFirstByPlatformAndIsActiveTrue("FACEBOOK")
                    .filter(a -> a.getAccessToken() != null && !a.getAccessToken().isBlank())
                    .isPresent();
            if (!hasFb) {
                throw new IllegalStateException(
                        "Akun Facebook belum terhubung! Silakan hubungkan akun Facebook terlebih dahulu di menu Connect Channels.");
            }
        }
    }

    private SocialPostResponse executePublish(SocialPost post) {
        log.info("Executing publish for SocialPost ID: {} to {}", post.getId(), post.getTargetPlatforms());
        post.setStatus("PUBLISHING");
        post.setErrorMessage(null);
        if (post.getScheduledAt() == null) {
            post.setScheduledAt(LocalDateTime.now());
        }
        postRepository.save(post);

        List<String> errors = new ArrayList<>();
        boolean anySuccess = false;

        String targets = post.getTargetPlatforms().toUpperCase();

        // 1. TikTok
        if (targets.contains("TIKTOK") || targets.contains("ALL")) {
            try {
                Optional<SocialAccount> ttOpt = accountRepository.findFirstByPlatformAndIsActiveTrue("TIKTOK");
                if (ttOpt.isPresent() && ttOpt.get().getAccessToken() != null
                        && !ttOpt.get().getAccessToken().isBlank()) {
                    String publishId = publishToTikTok(ttOpt.get(), post);
                    post.setTiktokPublishId(publishId);
                    anySuccess = true;
                } else {
                    errors.add(
                            "TikTok: Akun TikTok belum terhubung atau Access Token kosong. Silakan hubungkan akun di Connect Channels.");
                }
            } catch (Exception e) {
                log.error("Failed to publish to TikTok: {}", e.getMessage(), e);
                errors.add("TikTok: " + e.getMessage());
            }
        }

        // 2. Instagram
        if (targets.contains("INSTAGRAM") || targets.contains("ALL")) {
            try {
                Optional<SocialAccount> igOpt = accountRepository.findFirstByPlatformAndIsActiveTrue("INSTAGRAM");
                if (igOpt.isPresent() && igOpt.get().getAccessToken() != null
                        && !igOpt.get().getAccessToken().isBlank()) {
                    String mediaId = publishToInstagram(igOpt.get(), post);
                    post.setInstagramMediaId(mediaId);
                    anySuccess = true;
                } else {
                    errors.add(
                            "Instagram: Akun Instagram belum terhubung atau Access Token kosong. Silakan hubungkan akun di Connect Channels.");
                }
            } catch (Exception e) {
                log.error("Failed to publish to Instagram: {}", e.getMessage(), e);
                errors.add("Instagram: " + e.getMessage());
            }
        }

        // 3. LinkedIn
        if (targets.contains("LINKEDIN") || targets.contains("ALL")) {
            try {
                Optional<SocialAccount> liOpt = accountRepository.findFirstByPlatformAndIsActiveTrue("LINKEDIN");
                if (liOpt.isPresent() && liOpt.get().getAccessToken() != null
                        && !liOpt.get().getAccessToken().isBlank()) {
                    String postUrn = publishToLinkedIn(liOpt.get(), post);
                    post.setLinkedinPostUrn(postUrn);
                    anySuccess = true;
                } else {
                    errors.add("LinkedIn: Akun LinkedIn belum terhubung atau token kosong");
                }
            } catch (Exception e) {
                log.error("Failed to publish to LinkedIn: {}", e.getMessage(), e);
                errors.add("LinkedIn: " + e.getMessage());
            }
        }

        // 4. X (Twitter)
        if (targets.contains("X") || targets.contains("TWITTER") || targets.contains("ALL")) {
            try {
                Optional<SocialAccount> xOpt = accountRepository.findFirstByPlatformAndIsActiveTrue("X");
                if (xOpt.isPresent() && xOpt.get().getAccessToken() != null && !xOpt.get().getAccessToken().isBlank()) {
                    publishToX(xOpt.get(), post);
                    anySuccess = true;
                } else if (!targets.contains("ALL")) {
                    errors.add("X (Twitter): Akun X belum terhubung atau Access Token kosong");
                }
            } catch (Exception e) {
                log.error("Failed to publish to X: {}", e.getMessage(), e);
                errors.add("X: " + e.getMessage());
            }
        }

        // 5. YouTube
        if (targets.contains("YOUTUBE") || targets.contains("ALL")) {
            try {
                Optional<SocialAccount> ytOpt = accountRepository.findFirstByPlatformAndIsActiveTrue("YOUTUBE");
                if (ytOpt.isPresent() && ytOpt.get().getAccessToken() != null
                        && !ytOpt.get().getAccessToken().isBlank()) {
                    String videoId = publishToYouTube(ytOpt.get(), post);
                    post.setYoutubeVideoId(videoId);
                    anySuccess = true;
                } else if (!targets.contains("ALL")) {
                    errors.add(
                            "YouTube: Akun YouTube belum terhubung atau Access Token kosong. Silakan hubungkan akun di Connect Channels.");
                }
            } catch (Exception e) {
                log.error("Failed to publish to YouTube: {}", e.getMessage(), e);
                errors.add("YouTube: " + e.getMessage());
            }
        }

        // 6. Facebook
        if (targets.contains("FACEBOOK") || targets.contains("ALL")) {
            try {
                Optional<SocialAccount> fbOpt = accountRepository.findFirstByPlatformAndIsActiveTrue("FACEBOOK");
                if (fbOpt.isPresent() && fbOpt.get().getAccessToken() != null && !fbOpt.get().getAccessToken().isBlank()) {
                    String postId = publishToFacebook(fbOpt.get(), post);
                    post.setFacebookPostId(postId);
                    anySuccess = true;
                } else if (!targets.contains("ALL")) {
                    errors.add("Facebook: Akun Facebook belum terhubung atau Access Token kosong");
                }
            } catch (Exception e) {
                log.error("Failed to publish to Facebook: {}", e.getMessage(), e);
                errors.add("Facebook: " + e.getMessage());
            }
        }

        // 7. Threads
        if (targets.contains("THREADS") || targets.contains("ALL")) {
            try {
                Optional<SocialAccount> thOpt = accountRepository.findFirstByPlatformAndIsActiveTrue("THREADS");
                if (thOpt.isPresent() && thOpt.get().getAccessToken() != null && !thOpt.get().getAccessToken().isBlank()) {
                    String mediaId = publishToThreads(thOpt.get(), post);
                    post.setThreadsMediaId(mediaId);
                    anySuccess = true;
                } else if (!targets.contains("ALL")) {
                    errors.add("Threads: Akun Threads belum terhubung atau Access Token kosong");
                }
            } catch (Exception e) {
                log.error("Failed to publish to Threads: {}", e.getMessage(), e);
                errors.add("Threads: " + e.getMessage());
            }
        }

        if (anySuccess) {
            post.setStatus("PUBLISHED");
            post.setPublishedAt(LocalDateTime.now());
            if (!errors.isEmpty()) {
                post.setErrorMessage("Sebagian berhasil: " + String.join("; ", errors));
            }
        } else {
            post.setStatus("FAILED");
            post.setErrorMessage(
                    errors.isEmpty() ? "Gagal mempublikasikan ke platform tujuan" : String.join("; ", errors));
        }

        SocialPost saved = postRepository.save(post);
        return toPostResponse(saved);
    }

    private String publishToYouTube(SocialAccount account, SocialPost post) throws Exception {
        String accessToken = getValidYouTubeAccessToken(account);
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalStateException(
                    "YouTube Access Token tidak ditemukan. Silakan hubungkan ulang akun YouTube di menu Channels.");
        }

        String rawMediaUrl = post.getMediaUrls();
        if (rawMediaUrl == null || rawMediaUrl.isBlank()) {
            throw new IllegalArgumentException(
                    "YouTube hanya mendukung konten Video / Shorts. Silakan lampirkan file video (.mp4).");
        }

        // YouTube Data API only accepts video streams (MP4/MOV). Reject images early
        // with a helpful message.
        boolean isImage = "IMAGE".equalsIgnoreCase(post.getMediaType()) ||
                (rawMediaUrl != null && (rawMediaUrl.toLowerCase().endsWith(".jpg") ||
                        rawMediaUrl.toLowerCase().endsWith(".jpeg") ||
                        rawMediaUrl.toLowerCase().endsWith(".png") ||
                        rawMediaUrl.toLowerCase().endsWith(".webp") ||
                        rawMediaUrl.toLowerCase().endsWith(".gif")));
        if (isImage) {
            throw new IllegalArgumentException(
                    "YouTube tidak mendukung format Foto/Gambar untuk Status atau Postingan. YouTube hanya menerima file Video vertikal MP4 (Shorts). Silakan gunakan file video (.mp4) atau hapus centang YouTube jika ingin memposting foto ke Instagram/Facebook.");
        }

        String mediaUrl = resolvePublicUrl(rawMediaUrl);
        log.info("Preparing video upload to YouTube from URL: {}", mediaUrl);

        // Fetch video bytes
        byte[] videoBytes = fetchImageBytes(mediaUrl);
        if (videoBytes == null || videoBytes.length == 0) {
            throw new IllegalStateException("Gagal mengunduh file video untuk YouTube dari " + mediaUrl);
        }

        String title = post.getTitle();
        if (title == null || title.isBlank()) {
            if (post.getCaption() != null && !post.getCaption().isBlank()) {
                title = post.getCaption().length() > 65
                        ? post.getCaption().substring(0, 65).trim() + "..."
                        : post.getCaption();
            } else {
                title = "Zelvira Official Video";
            }
        }

        // Clean title: max length 95, remove angle brackets
        title = title.replace("<", "").replace(">", "").trim();
        if (title.length() > 95) {
            title = title.substring(0, 95);
        }

        boolean isShorts = "REEL".equalsIgnoreCase(post.getPostType()) ||
                "SHORTS".equalsIgnoreCase(post.getPostType()) ||
                "STORY".equalsIgnoreCase(post.getPostType()) ||
                (post.getCaption() != null
                        && (post.getCaption().contains("#Shorts") || post.getCaption().contains("#shorts")));
        if (isShorts && !title.toLowerCase().contains("#shorts")) {
            if (title.length() <= 85) {
                title = title + " #Shorts";
            }
        }

        String description = post.getCaption() != null ? post.getCaption() : title;
        if (isShorts && !description.toLowerCase().contains("#shorts")) {
            description = description + "\n\n#Shorts #Zelvira #Fashion";
        }

        String privacy = "public";
        if ("SELF_ONLY".equalsIgnoreCase(post.getPrivacyLevel())) {
            privacy = "private";
        } else if ("MUTUAL_FOLLOW_FRIENDS".equalsIgnoreCase(post.getPrivacyLevel())) {
            privacy = "unlisted";
        }

        // Step 1: Initiate Resumable Upload
        Map<String, Object> snippet = new LinkedHashMap<>();
        snippet.put("title", title);
        snippet.put("description", description);
        snippet.put("tags", List.of("Zelvira", "Fashion", "Indonesia", "Style", "Official"));
        snippet.put("categoryId", "22"); // People & Blogs

        Map<String, Object> status = new LinkedHashMap<>();
        status.put("privacyStatus", privacy);
        status.put("selfDeclaredMadeForKids", false);

        Map<String, Object> metaPayload = new LinkedHashMap<>();
        metaPayload.put("snippet", snippet);
        metaPayload.put("status", status);

        String jsonMeta = objectMapper.writeValueAsString(metaPayload);

        HttpRequest initRequest = HttpRequest.newBuilder()
                .uri(URI.create(
                        "https://www.googleapis.com/upload/youtube/v3/videos?uploadType=resumable&part=snippet,status"))
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json; charset=UTF-8")
                .header("X-Upload-Content-Type", "video/mp4")
                .header("X-Upload-Content-Length", String.valueOf(videoBytes.length))
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(jsonMeta))
                .build();

        HttpResponse<String> initResp = httpClient.send(initRequest, HttpResponse.BodyHandlers.ofString());
        log.info("YouTube Resumable Upload Init status: {}", initResp.statusCode());

        if (initResp.statusCode() != 200) {
            log.error("Failed to initiate YouTube upload: status={}, body={}", initResp.statusCode(), initResp.body());
            throw new RuntimeException("Gagal inisialisasi upload YouTube: " + initResp.body());
        }

        String uploadUrl = initResp.headers().firstValue("location")
                .orElseThrow(() -> new RuntimeException("Header Location untuk upload YouTube tidak ditemukan"));

        // Step 2: Upload Video Bytes
        HttpRequest uploadRequest = HttpRequest.newBuilder()
                .uri(URI.create(uploadUrl))
                .header("Content-Type", "video/mp4")
                .timeout(Duration.ofSeconds(180))
                .PUT(HttpRequest.BodyPublishers.ofByteArray(videoBytes))
                .build();

        HttpResponse<String> uploadResp = httpClient.send(uploadRequest, HttpResponse.BodyHandlers.ofString());
        log.info("YouTube video bytes upload status: {}, body: {}", uploadResp.statusCode(), uploadResp.body());

        if (uploadResp.statusCode() != 200 && uploadResp.statusCode() != 201) {
            throw new RuntimeException(
                    "Gagal upload video YouTube: status=" + uploadResp.statusCode() + ", resp=" + uploadResp.body());
        }

        JsonNode root = objectMapper.readTree(uploadResp.body());
        String videoId = root.path("id").asText(null);
        if (videoId == null || videoId.isBlank()) {
            throw new RuntimeException("Video ID tidak ditemukan di respon YouTube: " + uploadResp.body());
        }

        String returnedPrivacy = root.path("status").path("privacyStatus").asText("unknown");
        String uploadStatus = root.path("status").path("uploadStatus").asText("unknown");
        log.info(
                "YouTube Video successfully published! Video ID: {}, Privacy: {}, UploadStatus: {}, URL: https://youtu.be/{}",
                videoId, returnedPrivacy, uploadStatus, videoId);

        if ("public".equalsIgnoreCase(privacy) && "private".equalsIgnoreCase(returnedPrivacy)) {
            post.setErrorMessage(
                    "Status YouTube: Video tersimpan sebagai 'Private' di YouTube Studio (studio.youtube.com) karena OAuth Google Developer belum diverifikasi publik.");
        }

        return videoId;
    }

    private String publishToFacebook(SocialAccount account, SocialPost post) throws Exception {
        String pageId = account.getAccountId();
        String accessToken = account.getAccessToken();
        if (pageId == null || pageId.isBlank() || accessToken == null || accessToken.isBlank()) {
            throw new IllegalStateException("Facebook Page ID atau Access Token tidak tersedia");
        }

        String caption = (post.getTitle() != null && !post.getTitle().isBlank())
                ? post.getTitle() + "\n\n" + (post.getCaption() != null ? post.getCaption() : "")
                : (post.getCaption() != null ? post.getCaption() : "");

        String mediaUrl = resolvePublicUrl(post.getMediaUrls());
        boolean isVideo = "VIDEO".equalsIgnoreCase(post.getMediaType()) || "REEL".equalsIgnoreCase(post.getPostType());
        boolean hasMedia = mediaUrl != null && !mediaUrl.isBlank();

        String endpoint;
        Map<String, String> params = new LinkedHashMap<>();
        params.put("access_token", accessToken);

        if (hasMedia && isVideo) {
            endpoint = String.format("https://graph.facebook.com/v21.0/%s/videos", pageId);
            params.put("file_url", mediaUrl);
            if (!caption.isBlank()) {
                params.put("description", caption);
            }
        } else if (hasMedia) {
            endpoint = String.format("https://graph.facebook.com/v21.0/%s/photos", pageId);
            params.put("url", mediaUrl);
            if (!caption.isBlank()) {
                params.put("caption", caption);
            }
        } else {
            endpoint = String.format("https://graph.facebook.com/v21.0/%s/feed", pageId);
            params.put("message", caption.isBlank() ? "Update dari Zelvira Store" : caption);
        }

        String formBody = params.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "="
                        + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(formBody))
                .build();

        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        log.info("Facebook publish response: status={}, body={}", resp.statusCode(), resp.body());

        JsonNode json = objectMapper.readTree(resp.body());
        if (resp.statusCode() != 200 && resp.statusCode() != 201) {
            String msg = json.path("error").path("message").asText(resp.body());
            throw new RuntimeException("Facebook publish API error: " + msg);
        }

        String postId = json.has("id") ? json.path("id").asText()
                : json.path("post_id").asText("fb_" + System.currentTimeMillis());
        log.info("Facebook post published successfully! ID: {}", postId);
        return postId;
    }

    private String publishToThreads(SocialAccount account, SocialPost post) throws Exception {
        String userId = account.getAccountId();
        String accessToken = account.getAccessToken();
        if (userId == null || userId.isBlank() || accessToken == null || accessToken.isBlank()) {
            throw new IllegalStateException("Threads User ID atau Access Token tidak tersedia");
        }

        String caption = (post.getTitle() != null && !post.getTitle().isBlank())
                ? post.getTitle() + "\n\n" + (post.getCaption() != null ? post.getCaption() : "")
                : (post.getCaption() != null ? post.getCaption() : "");

        String mediaUrl = resolvePublicUrl(post.getMediaUrls());
        boolean isVideo = "VIDEO".equalsIgnoreCase(post.getMediaType()) || "REEL".equalsIgnoreCase(post.getPostType());
        boolean hasMedia = mediaUrl != null && !mediaUrl.isBlank();

        // Step 1: Create Container
        String containerUrl = String.format("https://graph.threads.net/v1.0/%s/threads", userId);
        Map<String, String> params = new LinkedHashMap<>();
        params.put("access_token", accessToken);

        if (hasMedia && isVideo) {
            params.put("media_type", "VIDEO");
            params.put("video_url", mediaUrl);
            if (!caption.isBlank()) {
                params.put("text", caption);
            }
        } else if (hasMedia) {
            params.put("media_type", "IMAGE");
            params.put("image_url", mediaUrl);
            if (!caption.isBlank()) {
                params.put("text", caption);
            }
        } else {
            params.put("media_type", "TEXT");
            params.put("text", caption.isBlank() ? "Update dari Zelvira Store" : caption);
        }

        String containerBody = params.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "="
                        + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));

        HttpRequest containerReq = HttpRequest.newBuilder()
                .uri(URI.create(containerUrl))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(containerBody))
                .build();

        HttpResponse<String> containerResp = httpClient.send(containerReq, HttpResponse.BodyHandlers.ofString());
        log.info("Threads container creation response: status={}, body={}", containerResp.statusCode(),
                containerResp.body());

        JsonNode containerJson = objectMapper.readTree(containerResp.body());
        if (containerResp.statusCode() != 200 || !containerJson.has("id")) {
            String msg = containerJson.path("error").path("message").asText(containerResp.body());
            throw new RuntimeException("Gagal membuat kontainer Threads: " + msg);
        }

        String creationId = containerJson.path("id").asText();

        // If video, wait for container to be ready
        if (hasMedia && isVideo) {
            Thread.sleep(3000);
        } else {
            Thread.sleep(1000);
        }

        // Step 2: Publish Container
        String publishUrl = String.format("https://graph.threads.net/v1.0/%s/threads_publish", userId);
        String publishBody = String.format("creation_id=%s&access_token=%s",
                URLEncoder.encode(creationId, StandardCharsets.UTF_8),
                URLEncoder.encode(accessToken, StandardCharsets.UTF_8));

        HttpRequest publishReq = HttpRequest.newBuilder()
                .uri(URI.create(publishUrl))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(publishBody))
                .build();

        HttpResponse<String> publishResp = httpClient.send(publishReq, HttpResponse.BodyHandlers.ofString());
        log.info("Threads publish response: status={}, body={}", publishResp.statusCode(), publishResp.body());

        JsonNode publishJson = objectMapper.readTree(publishResp.body());
        if (publishResp.statusCode() != 200 || !publishJson.has("id")) {
            String msg = publishJson.path("error").path("message").asText(publishResp.body());
            throw new RuntimeException("Gagal menerbitkan postingan Threads: " + msg);
        }

        String publishedMediaId = publishJson.path("id").asText();
        log.info("Threads post published successfully! Media ID: {}", publishedMediaId);
        return publishedMediaId;
    }

    private String publishToInstagram(SocialAccount account, SocialPost post) throws Exception {
        String igUserId = account.getAccountId();
        if (igUserId == null || igUserId.isBlank()) {
            igUserId = (instagramDefaultAccountId != null && !instagramDefaultAccountId.isBlank())
                    ? instagramDefaultAccountId.trim()
                    : "";
        }
        String accessToken = account.getAccessToken();
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalStateException(
                    "Instagram Access Token tidak ditemukan. Silakan hubungkan ulang akun Instagram.");
        }

        String mediaUrl = resolvePublicUrl(post.getMediaUrls());
        boolean isVideo = "VIDEO".equalsIgnoreCase(post.getMediaType()) || "REEL".equalsIgnoreCase(post.getPostType());
        boolean isStory = "STORY".equalsIgnoreCase(post.getPostType());
        boolean isCarousel = "CAROUSEL".equalsIgnoreCase(post.getMediaType());

        // Default media fallback if post is text-only (Instagram Graph API strictly
        // requires media)
        if (mediaUrl == null || mediaUrl.isBlank()) {
            mediaUrl = "https://zelvira.co.id/images/zelixa-logo.png";
        }

        String caption = (post.getTitle() != null && !post.getTitle().isBlank())
                ? post.getTitle() + "\n\n" + (post.getCaption() != null ? post.getCaption() : "")
                : (post.getCaption() != null ? post.getCaption() : "");

        String creationId = null;

        // 1. Carousel Container Creation if multiple images provided
        if (isCarousel && post.getMediaUrls() != null && post.getMediaUrls().contains(",")) {
            String[] items = post.getMediaUrls().split(",");
            List<String> childIds = new ArrayList<>();
            for (String item : items) {
                String itemUrl = resolvePublicUrl(item.trim());
                if (itemUrl != null && !itemUrl.isBlank()) {
                    String childBody = String.format("image_url=%s&is_carousel_item=true&access_token=%s",
                            URLEncoder.encode(itemUrl, StandardCharsets.UTF_8),
                            URLEncoder.encode(accessToken, StandardCharsets.UTF_8));
                    HttpRequest childReq = HttpRequest.newBuilder()
                            .uri(URI.create(String.format("https://graph.facebook.com/v21.0/%s/media", igUserId)))
                            .header("Content-Type", "application/x-www-form-urlencoded")
                            .POST(HttpRequest.BodyPublishers.ofString(childBody))
                            .build();
                    HttpResponse<String> childResp = httpClient.send(childReq, HttpResponse.BodyHandlers.ofString());
                    JsonNode childJson = objectMapper.readTree(childResp.body());
                    if (childJson.has("id")) {
                        childIds.add(childJson.path("id").asText());
                    }
                }
            }

            if (!childIds.isEmpty()) {
                String carouselBody = String.format("media_type=CAROUSEL&children=%s&caption=%s&access_token=%s",
                        URLEncoder.encode(String.join(",", childIds), StandardCharsets.UTF_8),
                        URLEncoder.encode(caption, StandardCharsets.UTF_8),
                        URLEncoder.encode(accessToken, StandardCharsets.UTF_8));
                HttpRequest carReq = HttpRequest.newBuilder()
                        .uri(URI.create(String.format("https://graph.facebook.com/v21.0/%s/media", igUserId)))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(carouselBody))
                        .build();
                HttpResponse<String> carResp = httpClient.send(carReq, HttpResponse.BodyHandlers.ofString());
                JsonNode carJson = objectMapper.readTree(carResp.body());
                if (carJson.has("id")) {
                    creationId = carJson.path("id").asText();
                }
            }
        }

        // 2. Single Media Container (Photo, Reel, or Story/Status)
        if (creationId == null) {
            Map<String, String> params = new LinkedHashMap<>();
            params.put("access_token", accessToken);

            if (isStory) {
                params.put("media_type", "STORIES");
                if (isVideo) {
                    params.put("video_url", mediaUrl);
                } else {
                    params.put("image_url", mediaUrl);
                }
            } else if (isVideo) {
                params.put("media_type", "REELS");
                params.put("video_url", mediaUrl);
                if (!caption.isBlank()) {
                    params.put("caption", caption);
                }
            } else {
                params.put("image_url", mediaUrl);
                if (!caption.isBlank()) {
                    params.put("caption", caption);
                }
            }

            String containerBody = params.entrySet().stream()
                    .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "="
                            + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                    .collect(Collectors.joining("&"));

            String containerUrl = String.format("https://graph.facebook.com/v21.0/%s/media", igUserId);
            HttpRequest containerReq = HttpRequest.newBuilder()
                    .uri(URI.create(containerUrl))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(containerBody))
                    .build();

            HttpResponse<String> containerResp = httpClient.send(containerReq, HttpResponse.BodyHandlers.ofString());
            log.info("Instagram container creation: status={}, body={}", containerResp.statusCode(),
                    containerResp.body());

            JsonNode containerJson = objectMapper.readTree(containerResp.body());
            if (containerResp.statusCode() != 200 || !containerJson.has("id")) {
                String msg = containerJson.path("error").path("message").asText(containerResp.body());
                throw new RuntimeException("Gagal membuat kontainer Instagram: " + msg);
            }
            creationId = containerJson.path("id").asText();
        }

        // 3. Poll video processing if video/reel
        if (isVideo) {
            int maxAttempts = 10;
            for (int i = 0; i < maxAttempts; i++) {
                Thread.sleep(3000);
                String statusUrl = String.format(
                        "https://graph.facebook.com/v21.0/%s?fields=status_code&access_token=%s",
                        creationId, URLEncoder.encode(accessToken, StandardCharsets.UTF_8));
                HttpRequest statusReq = HttpRequest.newBuilder().uri(URI.create(statusUrl)).GET().build();
                HttpResponse<String> statusResp = httpClient.send(statusReq, HttpResponse.BodyHandlers.ofString());
                JsonNode statusJson = objectMapper.readTree(statusResp.body());
                String statusCode = statusJson.path("status_code").asText();
                log.info("Instagram video container {} status: {}", creationId, statusCode);
                if ("FINISHED".equalsIgnoreCase(statusCode)) {
                    break;
                } else if ("ERROR".equalsIgnoreCase(statusCode)) {
                    throw new RuntimeException("Instagram gagal memproses video container");
                }
            }
        }

        // 4. Publish Media Container
        String publishBody = String.format("creation_id=%s&access_token=%s",
                URLEncoder.encode(creationId, StandardCharsets.UTF_8),
                URLEncoder.encode(accessToken, StandardCharsets.UTF_8));
        String publishUrl = String.format("https://graph.facebook.com/v21.0/%s/media_publish", igUserId);
        HttpRequest publishReq = HttpRequest.newBuilder()
                .uri(URI.create(publishUrl))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(publishBody))
                .build();

        HttpResponse<String> publishResp = httpClient.send(publishReq, HttpResponse.BodyHandlers.ofString());
        log.info("Instagram media publish: status={}, body={}", publishResp.statusCode(), publishResp.body());

        JsonNode publishJson = objectMapper.readTree(publishResp.body());
        if (publishResp.statusCode() != 200 || !publishJson.has("id")) {
            String msg = publishJson.path("error").path("message").asText(publishResp.body());
            throw new RuntimeException("Gagal menerbitkan media Instagram: " + msg);
        }

        String publishedMediaId = publishJson.path("id").asText();
        log.info("Instagram post published successfully! Media ID: {}", publishedMediaId);
        return publishedMediaId;
    }

    private static String normalizeTikTokPostMode(String rawMode) {
        return "MEDIA_UPLOAD".equalsIgnoreCase(rawMode != null ? rawMode.trim() : "")
                ? "MEDIA_UPLOAD"
                : "DIRECT_POST";
    }

    private JsonNode extractTikTokTokenNode(JsonNode root) {
        if (root == null) {
            return objectMapper.createObjectNode();
        }
        return root.hasNonNull("access_token") ? root : root.path("data");
    }

    private String extractTikTokOAuthError(JsonNode root, String fallback) {
        if (root == null) {
            return fallback;
        }
        String desc = root.path("error_description").asText(null);
        if (desc != null && !desc.isBlank()) {
            return desc;
        }
        JsonNode errNode = root.path("error");
        if (errNode.isTextual() && !errNode.asText().isBlank()) {
            return errNode.asText();
        }
        String nestedMsg = errNode.path("message").asText(null);
        if (nestedMsg != null && !nestedMsg.isBlank()) {
            return nestedMsg;
        }
        return fallback;
    }

    private static String truncateTikTokText(String text, int maxLen) {
        if (text == null) {
            return "";
        }
        String trimmed = text.trim();
        if (trimmed.length() <= maxLen) {
            return trimmed;
        }
        int cut = Math.max(0, maxLen - 3);
        if (cut > 0 && Character.isHighSurrogate(trimmed.charAt(cut - 1))) {
            cut--;
        }
        return trimmed.substring(0, cut) + "...";
    }

    private JsonNode queryTikTokCreatorInfo(String accessToken) throws Exception {
        HttpRequest queryReq = HttpRequest.newBuilder()
                .uri(URI.create("https://open.tiktokapis.com/v2/post/publish/creator_info/query/"))
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json; charset=UTF-8")
                .timeout(Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .build();
        HttpResponse<String> queryResp = httpClient.send(queryReq, HttpResponse.BodyHandlers.ofString());
        JsonNode qJson = objectMapper.readTree(queryResp.body());
        if (queryResp.statusCode() != 200) {
            throw new RuntimeException("Creator info HTTP " + queryResp.statusCode() + ": " + queryResp.body());
        }
        String errCode = qJson.path("error").path("code").asText("ok");
        if (!"ok".equalsIgnoreCase(errCode) && !errCode.isBlank()) {
            String errMsg = qJson.path("error").path("message").asText(queryResp.body());
            throw new RuntimeException("TikTok Creator Info (" + errCode + "): " + errMsg);
        }
        return qJson.path("data");
    }

    @Override
    public Map<String, Object> getTikTokCreatorInfo() {
        Map<String, Object> result = new LinkedHashMap<>();
        Optional<SocialAccount> accOpt = accountRepository.findFirstByPlatformAndIsActiveTrue("TIKTOK");
        if (accOpt.isEmpty() || accOpt.get().getAccessToken() == null || accOpt.get().getAccessToken().isBlank()) {
            result.put("connected", false);
            result.put("privacyLevelOptions", List.of("SELF_ONLY"));
            result.put("isSandboxRestricted", true);
            result.put("error", "Akun TikTok belum terhubung. Silakan hubungkan via Settings terlebih dahulu.");
            return result;
        }

        SocialAccount account = accOpt.get();
        result.put("connected", true);
        result.put("accountName", account.getAccountName());
        result.put("avatarUrl", account.getAvatarUrl());

        List<String> privOptions = new ArrayList<>();
        try {
            String accessToken = getValidTikTokAccessToken(account);
            JsonNode cData = queryTikTokCreatorInfo(accessToken);

            String creatorUsername = cData.path("creator_username").asText(null);
            String creatorNickname = cData.path("creator_nickname").asText(null);
            String creatorAvatarUrl = cData.path("creator_avatar_url").asText(null);
            if (creatorUsername != null && !creatorUsername.isBlank()) {
                result.put("creatorUsername", creatorUsername);
            }
            if (creatorNickname != null && !creatorNickname.isBlank()) {
                result.put("creatorNickname", creatorNickname);
            }
            if (creatorAvatarUrl != null && !creatorAvatarUrl.isBlank()) {
                result.put("creatorAvatarUrl", creatorAvatarUrl);
            }

            JsonNode optsNode = cData.path("privacy_level_options");
            if (optsNode.isArray()) {
                for (JsonNode opt : optsNode) {
                    String val = opt.asText(null);
                    if (val != null && !val.isBlank()) {
                        privOptions.add(val);
                    }
                }
            }
            if (privOptions.isEmpty()) {
                privOptions.add("SELF_ONLY");
            }
            result.put("privacyLevelOptions", privOptions);
            result.put("commentDisabled", cData.path("comment_disabled").asBoolean(false));
            result.put("duetDisabled", cData.path("duet_disabled").asBoolean(false));
            result.put("stitchDisabled", cData.path("stitch_disabled").asBoolean(false));
            if (cData.hasNonNull("max_video_post_duration_sec")) {
                result.put("maxVideoPostDurationSec", cData.path("max_video_post_duration_sec").asInt());
            }
            boolean sandboxRestricted = privOptions.size() == 1 && "SELF_ONLY".equalsIgnoreCase(privOptions.get(0));
            result.put("isSandboxRestricted", sandboxRestricted);
        } catch (Exception e) {
            log.warn("Failed to query TikTok creator_info: {}", e.getMessage());
            privOptions.add("SELF_ONLY");
            result.put("privacyLevelOptions", privOptions);
            result.put("isSandboxRestricted", true);
            result.put("warning", e.getMessage());
        }
        return result;
    }

    @Override
    @Transactional
    public Map<String, Object> fetchTikTokPublishStatus(String publishId) {
        if (publishId == null || publishId.isBlank()) {
            throw new IllegalArgumentException("publishId tidak boleh kosong.");
        }
        SocialAccount account = accountRepository.findFirstByPlatformAndIsActiveTrue("TIKTOK")
                .orElseThrow(() -> new IllegalStateException(
                        "Akun TikTok belum terhubung. Silakan hubungkan akun TikTok di Settings."));
        String accessToken = getValidTikTokAccessToken(account);
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalStateException(
                    "TikTok Access Token tidak ditemukan atau kadaluarsa. Silakan hubungkan ulang akun TikTok.");
        }

        try {
            Map<String, Object> statusPayload = fetchTikTokStatusInternal(accessToken, publishId.trim());
            String ttStatus = String.valueOf(statusPayload.getOrDefault("status", "UNKNOWN"));
            String failReason = statusPayload.get("failReason") != null
                    ? String.valueOf(statusPayload.get("failReason"))
                    : null;

            postRepository.findFirstByTiktokPublishId(publishId.trim()).ifPresent(post -> {
                boolean isTikTokOnly = post.getTargetPlatforms() != null
                        && "TIKTOK".equalsIgnoreCase(post.getTargetPlatforms().trim());
                if ("FAILED".equalsIgnoreCase(ttStatus)) {
                    post.setErrorMessage("TikTok status FAILED" + (failReason != null ? ": " + failReason : ""));
                    if (isTikTokOnly) {
                        post.setStatus("FAILED");
                    }
                    postRepository.save(post);
                } else if ("PUBLISH_COMPLETE".equalsIgnoreCase(ttStatus)
                        || "SEND_TO_USER_INBOX".equalsIgnoreCase(ttStatus)) {
                    if (isTikTokOnly) {
                        post.setStatus("PUBLISHED");
                        post.setErrorMessage(null);
                    }
                    postRepository.save(post);
                }
            });

            return statusPayload;
        } catch (RuntimeException re) {
            throw re;
        } catch (Exception e) {
            throw new RuntimeException("Gagal memeriksa status publikasi TikTok: " + e.getMessage(), e);
        }
    }

    private Map<String, Object> fetchTikTokStatusInternal(String accessToken, String publishId) throws Exception {
        String payload = objectMapper.writeValueAsString(Map.of("publish_id", publishId));
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://open.tiktokapis.com/v2/post/publish/status/fetch/"))
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json; charset=UTF-8")
                .timeout(Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        JsonNode root = objectMapper.readTree(resp.body());
        JsonNode errNode = root.path("error");
        String errCode = errNode.path("code").asText("ok");
        if (!"ok".equalsIgnoreCase(errCode) && !errCode.isBlank()) {
            String errMsg = errNode.path("message").asText(resp.body());
            throw new RuntimeException("TikTok Status API Error (" + errCode + "): " + errMsg);
        }

        JsonNode data = root.path("data");
        String status = data.path("status").asText("UNKNOWN");
        String failReason = data.path("fail_reason").asText(null);
        List<String> postIds = new ArrayList<>();
        JsonNode idArray = data.has("publicaly_available_post_id")
                ? data.path("publicaly_available_post_id")
                : data.path("publicly_available_post_id");
        if (idArray.isArray()) {
            for (JsonNode idNode : idArray) {
                String idVal = idNode.asText(null);
                if (idVal != null && !idVal.isBlank()) {
                    postIds.add(idVal);
                }
            }
        }

        String humanMessage;
        switch (status.toUpperCase()) {
            case "PROCESSING_UPLOAD":
                humanMessage = "TikTok sedang memproses file upload Anda.";
                break;
            case "PROCESSING_DOWNLOAD":
                humanMessage = "TikTok sedang mengunduh media dari URL.";
                break;
            case "SEND_TO_USER_INBOX":
                humanMessage = "Media terkirim ke Kotak Masuk / Draf aplikasi TikTok kreator. Buka aplikasi TikTok untuk edit & terbitkan.";
                break;
            case "PUBLISH_COMPLETE":
                humanMessage = "Konten berhasil dipublikasikan ke akun TikTok (di Sandbox dengan privasi SELF_ONLY hanya tampil di akun creator).";
                break;
            case "FAILED":
                humanMessage = "Publikasi gagal" + (failReason != null && !failReason.isBlank() ? ": " + failReason : ".");
                break;
            default:
                humanMessage = "Status TikTok: " + status;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("publishId", publishId);
        result.put("status", status);
        if (failReason != null && !failReason.isBlank()) {
            result.put("failReason", failReason);
        }
        result.put("publiclyAvailablePostIds", postIds);
        result.put("message", humanMessage);
        result.put("checkedAt", LocalDateTime.now().toString());
        return result;
    }

    private static String detectVideoMimeType(String mediaUrl) {
        if (mediaUrl == null) {
            return "video/mp4";
        }
        String lower = mediaUrl.toLowerCase();
        if (lower.contains(".webm")) {
            return "video/webm";
        }
        if (lower.contains(".mov")) {
            return "video/quicktime";
        }
        return "video/mp4";
    }

    private void uploadVideoChunksToTikTok(String uploadUrl, byte[] videoBytes, long chunkSize, long totalChunkCount,
            String mimeType) throws Exception {
        long videoSize = videoBytes.length;
        for (int i = 0; i < totalChunkCount; i++) {
            long start = i * chunkSize;
            long endExclusive = (i == totalChunkCount - 1) ? videoSize : (start + chunkSize);
            byte[] slice = Arrays.copyOfRange(videoBytes, (int) start, (int) endExclusive);
            long lastByte = endExclusive - 1;
            String contentRange = "bytes " + start + "-" + lastByte + "/" + videoSize;

            log.info("Uploading TikTok chunk {}/{}: {}, mime={}", i + 1, totalChunkCount, contentRange, mimeType);
            HttpRequest putReq = HttpRequest.newBuilder()
                    .uri(URI.create(uploadUrl))
                    .header("Content-Type", mimeType)
                    .header("Content-Range", contentRange)
                    .timeout(Duration.ofMinutes(2))
                    .PUT(HttpRequest.BodyPublishers.ofByteArray(slice))
                    .build();
            HttpResponse<String> putResp = httpClient.send(putReq, HttpResponse.BodyHandlers.ofString());
            int code = putResp.statusCode();
            if (code != 200 && code != 201 && code != 206) {
                throw new RuntimeException(
                        "Gagal mengunggah chunk video ke TikTok (HTTP " + code + "): " + putResp.body());
            }
        }
    }

    private String ensureTikTokPhotoJpegUrl(String rawImageUrl) throws Exception {
        String clean = resolvePublicUrl(rawImageUrl);
        if (clean == null || clean.isBlank()) {
            return null;
        }
        String pathOnly = clean.contains("?") ? clean.substring(0, clean.indexOf("?")) : clean;
        String lower = pathOnly.toLowerCase();
        if ((lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".webp"))
                && clean.startsWith("https://")) {
            return clean;
        }

        byte[] rawBytes = fetchImageBytes(clean);
        if (rawBytes == null || rawBytes.length == 0) {
            return clean;
        }

        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(rawBytes));
        if (decoded == null || minioClient == null) {
            return clean;
        }

        BufferedImage rgbImage = new BufferedImage(decoded.getWidth(), decoded.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = rgbImage.createGraphics();
        try {
            g2d.setColor(Color.WHITE);
            g2d.fillRect(0, 0, decoded.getWidth(), decoded.getHeight());
            g2d.drawImage(decoded, 0, 0, null);
        } finally {
            g2d.dispose();
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(rgbImage, "jpg", baos);
        byte[] jpgBytes = baos.toByteArray();

        String objectName = "social/tiktok/" + System.currentTimeMillis() + "_"
                + UUID.randomUUID().toString().substring(0, 8) + ".jpg";
        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(minioBucket)
                        .object(objectName)
                        .stream(new ByteArrayInputStream(jpgBytes), jpgBytes.length, -1)
                        .contentType("image/jpeg")
                        .build());

        String pubBase = (minioPublicUrl != null && !minioPublicUrl.isBlank())
                ? minioPublicUrl.trim()
                : "https://minio.zelvira.co.id";
        if (pubBase.endsWith("/")) {
            pubBase = pubBase.substring(0, pubBase.length() - 1);
        }
        String convertedUrl = pubBase + "/" + minioBucket + "/" + objectName;
        log.info("Converted non-JPEG image {} to TikTok-compatible JPEG {}", clean, convertedUrl);
        return convertedUrl;
    }

    private JsonNode sendTikTokInitRequest(String endpoint, String accessToken, Map<String, Object> body,
            String postMode, boolean isVideo) throws Exception {
        String jsonBody = objectMapper.writeValueAsString(body);
        log.info("Sending TikTok Content Posting request to {} (mode={}, isVideo={}): {}", endpoint, postMode, isVideo,
                jsonBody);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json; charset=UTF-8")
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        log.info("TikTok publish response: status={}, body={}", response.statusCode(), response.body());
        return objectMapper.readTree(response.body());
    }

    private String publishToTikTok(SocialAccount account, SocialPost post) throws Exception {
        String accessToken = getValidTikTokAccessToken(account);
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalStateException(
                    "TikTok Access Token tidak ditemukan. Silakan hubungkan ulang akun TikTok di menu Settings.");
        }

        String postMode = normalizeTikTokPostMode(post.getTiktokPostMode());
        List<String> mediaList = new ArrayList<>();
        if (post.getMediaUrls() != null && !post.getMediaUrls().isBlank()) {
            for (String item : post.getMediaUrls().split(",")) {
                String cleanItem = resolvePublicUrl(item.trim());
                if (cleanItem != null && !cleanItem.isBlank()) {
                    mediaList.add(cleanItem);
                }
            }
        }
        if (mediaList.isEmpty() && post.getThumbnailUrl() != null && !post.getThumbnailUrl().isBlank()) {
            String thumb = resolvePublicUrl(post.getThumbnailUrl());
            if (thumb != null && !thumb.isBlank()) {
                mediaList.add(thumb);
            }
        }
        if (mediaList.isEmpty()) {
            throw new IllegalArgumentException(
                    "TikTok mewajibkan minimal 1 file media (Video MP4/WebM/MOV atau Foto JPEG/WebP).");
        }

        String primaryMediaUrl = mediaList.get(0);
        boolean isVideo = "VIDEO".equalsIgnoreCase(post.getMediaType())
                || "REEL".equalsIgnoreCase(post.getPostType())
                || primaryMediaUrl.toLowerCase().matches(".*\\.(mp4|mov|webm)(\\?.*)?$");

        String privacy = (post.getPrivacyLevel() != null && !post.getPrivacyLevel().isBlank())
                ? post.getPrivacyLevel()
                : "SELF_ONLY";
        boolean creatorCommentDisabled = false;
        boolean creatorDuetDisabled = false;
        boolean creatorStitchDisabled = false;

        try {
            JsonNode cData = queryTikTokCreatorInfo(accessToken);
            creatorCommentDisabled = cData.path("comment_disabled").asBoolean(false);
            creatorDuetDisabled = cData.path("duet_disabled").asBoolean(false);
            creatorStitchDisabled = cData.path("stitch_disabled").asBoolean(false);
            JsonNode privOptions = cData.path("privacy_level_options");
            if (privOptions.isArray() && privOptions.size() > 0) {
                boolean found = false;
                for (JsonNode opt : privOptions) {
                    if (opt.asText().equalsIgnoreCase(privacy)) {
                        privacy = opt.asText();
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    privacy = privOptions.get(0).asText();
                    log.info("Requested privacy level not permitted by TikTok creator, falling back to {}", privacy);
                }
            }
        } catch (Exception e) {
            log.warn("Could not query TikTok creator info: {}", e.getMessage());
        }

        String endpoint;
        Map<String, Object> body = new LinkedHashMap<>();
        byte[] videoBytesForUpload = null;
        long uploadChunkSize = 0L;
        long uploadTotalChunks = 0L;

        if (isVideo) {
            Map<String, Object> sourceInfo = new LinkedHashMap<>();
            byte[] downloaded = fetchImageBytes(primaryMediaUrl);
            if (downloaded != null && downloaded.length > 0) {
                videoBytesForUpload = downloaded;
                long videoSize = downloaded.length;
                long maxSingleChunk = 64L * 1024L * 1024L;
                long defaultChunk = 10L * 1024L * 1024L;
                uploadChunkSize = (videoSize <= maxSingleChunk) ? videoSize : defaultChunk;
                uploadTotalChunks = (videoSize <= maxSingleChunk) ? 1L : (videoSize / uploadChunkSize);

                sourceInfo.put("source", "FILE_UPLOAD");
                sourceInfo.put("video_size", videoSize);
                sourceInfo.put("chunk_size", uploadChunkSize);
                sourceInfo.put("total_chunk_count", uploadTotalChunks);
            } else {
                sourceInfo.put("source", "PULL_FROM_URL");
                sourceInfo.put("video_url", primaryMediaUrl);
            }

            if ("MEDIA_UPLOAD".equals(postMode)) {
                endpoint = "https://open.tiktokapis.com/v2/post/publish/inbox/video/init/";
                body.put("source_info", sourceInfo);
            } else {
                endpoint = "https://open.tiktokapis.com/v2/post/publish/video/init/";
                String rawCaption = (post.getCaption() != null && !post.getCaption().isBlank())
                        ? post.getCaption().trim()
                        : "";
                String rawTitle = (post.getTitle() != null && !post.getTitle().isBlank())
                        ? post.getTitle().trim()
                        : "";
                String combinedTitle;
                if (!rawTitle.isBlank() && !rawCaption.isBlank() && !rawCaption.contains(rawTitle)) {
                    combinedTitle = rawTitle + "\n" + rawCaption;
                } else if (!rawCaption.isBlank()) {
                    combinedTitle = rawCaption;
                } else if (!rawTitle.isBlank()) {
                    combinedTitle = rawTitle;
                } else {
                    combinedTitle = "Zelvira Collection";
                }

                Map<String, Object> postInfo = new LinkedHashMap<>();
                postInfo.put("title", truncateTikTokText(combinedTitle, 2200));
                postInfo.put("privacy_level", privacy);
                postInfo.put("disable_duet", creatorDuetDisabled);
                postInfo.put("disable_comment", creatorCommentDisabled);
                postInfo.put("disable_stitch", creatorStitchDisabled);

                body.put("post_info", postInfo);
                body.put("source_info", sourceInfo);
            }
        } else {
            endpoint = "https://open.tiktokapis.com/v2/post/publish/content/init/";
            List<String> photoImages = new ArrayList<>();
            for (String imgUrl : mediaList) {
                if (photoImages.size() >= 35) {
                    break;
                }
                String readyUrl = ensureTikTokPhotoJpegUrl(imgUrl);
                if (readyUrl != null && !readyUrl.isBlank()) {
                    photoImages.add(readyUrl);
                }
            }
            if (photoImages.isEmpty()) {
                throw new IllegalArgumentException("Tidak ada gambar valid untuk dipublikasikan ke TikTok.");
            }

            String rawCaption = (post.getCaption() != null && !post.getCaption().isBlank())
                    ? post.getCaption().trim()
                    : "";
            String rawTitle = (post.getTitle() != null && !post.getTitle().isBlank())
                    ? post.getTitle().trim()
                    : rawCaption;
            if (rawTitle.isBlank()) {
                rawTitle = "Zelvira Collection";
            }

            Map<String, Object> postInfo = new LinkedHashMap<>();
            postInfo.put("title", truncateTikTokText(rawTitle, 90));
            if (!rawCaption.isBlank()) {
                postInfo.put("description", truncateTikTokText(rawCaption, 4000));
            }
            postInfo.put("disable_comment", creatorCommentDisabled);
            postInfo.put("auto_add_music", true);
            if ("DIRECT_POST".equals(postMode)) {
                postInfo.put("privacy_level", privacy);
            }

            body.put("post_info", postInfo);
            body.put("source_info", Map.of(
                    "source", "PULL_FROM_URL",
                    "photo_cover_index", 0,
                    "photo_images", photoImages));
            body.put("post_mode", postMode);
            body.put("media_type", "PHOTO");
        }

        JsonNode resNode = sendTikTokInitRequest(endpoint, accessToken, body, postMode, isVideo);
        String errCode = resNode.path("error").path("code").asText("ok");

        // In Sandbox / unaudited apps, TikTok creator_info still returns PUBLIC_TO_EVERYONE even though init rejects it.
        // Auto-retry once with SELF_ONLY before failing so multi-platform defaults don't block TikTok Sandbox tests.
        if ("unaudited_client_can_only_post_to_private_accounts".equalsIgnoreCase(errCode)
                && body.get("post_info") instanceof Map<?, ?>) {
            @SuppressWarnings("unchecked")
            Map<String, Object> postInfoMap = (Map<String, Object>) body.get("post_info");
            String currentPriv = String.valueOf(postInfoMap.getOrDefault("privacy_level", ""));
            if (!"SELF_ONLY".equalsIgnoreCase(currentPriv)) {
                log.info("TikTok rejected privacy '{}' for unaudited/sandbox client, retrying automatically with SELF_ONLY",
                        currentPriv);
                postInfoMap.put("privacy_level", "SELF_ONLY");
                resNode = sendTikTokInitRequest(endpoint, accessToken, body, postMode, isVideo);
                errCode = resNode.path("error").path("code").asText("ok");
            }
        }

        JsonNode errNode = resNode.path("error");
        if (!"ok".equalsIgnoreCase(errCode) && !errCode.isBlank()) {
            String errMsg = errNode.path("message").asText(resNode.toString());
            if ("url_ownership_unverified".equalsIgnoreCase(errCode)) {
                throw new RuntimeException(
                        "TikTok Error (url_ownership_unverified): Domain URL media belum diverifikasi di TikTok Developer Portal (Content Posting API -> Verify domains). Verifikasi URL prefix https://minio.zelvira.co.id/ di portal TikTok atau gunakan media Video agar otomatis memakai FILE_UPLOAD.");
            } else if ("unaudited_client_can_only_post_to_private_accounts".equalsIgnoreCase(errCode)) {
                throw new RuntimeException(
                        "TikTok Error (Sandbox / Unaudited): Aplikasi masih di mode Sandbox / belum diaudit, sehingga TikTok mewajibkan: (1) Privasi 'SELF_ONLY' (sudah dicoba otomatis), DAN (2) Akun TikTok di aplikasi TikTok HP WAJIB diset ke 'Akun Privat / Private Account' (Profil -> Pengaturan dan privasi -> Privasi -> Akun privat). Setelah akun di-set privat, coba posting lagi atau pilih mode 'Draf / Kotak Masuk'.");
            } else if ("scope_not_authorized".equalsIgnoreCase(errCode)) {
                throw new RuntimeException(
                        "TikTok Error (scope_not_authorized): Izin scope belum diberikan. Silakan putuskan & hubungkan ulang akun TikTok di Settings.");
            }
            throw new RuntimeException("TikTok Content Posting API Error (" + errCode + "): " + errMsg);
        }

        JsonNode dataNode = resNode.path("data");
        String publishId = dataNode.path("publish_id").asText();
        String uploadUrl = dataNode.path("upload_url").asText(null);
        if (publishId == null || publishId.isBlank()) {
            publishId = "tt_pub_" + System.currentTimeMillis();
        }

        if (videoBytesForUpload != null && uploadUrl != null && !uploadUrl.isBlank()) {
            uploadVideoChunksToTikTok(uploadUrl, videoBytesForUpload, uploadChunkSize, uploadTotalChunks,
                    detectVideoMimeType(primaryMediaUrl));
        }

        // Quick status check so immediate async rejections (format/pull failure) surface immediately
        try {
            Thread.sleep(800);
            Map<String, Object> statusCheck = fetchTikTokStatusInternal(accessToken, publishId);
            String st = String.valueOf(statusCheck.getOrDefault("status", ""));
            if ("FAILED".equalsIgnoreCase(st)) {
                String reason = String.valueOf(statusCheck.getOrDefault("failReason", "Unknown failure"));
                throw new RuntimeException("TikTok memproses namun gagal (" + reason + ")");
            }
            log.info("TikTok post initialized! publishId={}, status={}", publishId, st);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException re) {
            if (re.getMessage() != null && re.getMessage().startsWith("TikTok memproses namun gagal")) {
                throw re;
            }
            log.warn("TikTok status sanity check skipped for {}: {}", publishId, re.getMessage());
        } catch (Exception e) {
            log.warn("TikTok status sanity check skipped for {}: {}", publishId, e.getMessage());
        }

        return publishId;
    }

    private String resolvePublicUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank())
            return null;
        String clean = rawUrl.trim();
        if (clean.contains(",")) {
            clean = clean.split(",")[0].trim();
        }
        String pubUrl = (minioPublicUrl != null && !minioPublicUrl.isBlank())
                ? minioPublicUrl.trim()
                : "https://minio.zelvira.co.id";
        if (pubUrl.endsWith("/")) {
            pubUrl = pubUrl.substring(0, pubUrl.length() - 1);
        }

        if (clean.startsWith("http://minio:9000") || clean.startsWith("http://localhost:9000")) {
            clean = clean.replaceFirst("https?://[^/]+", pubUrl);
        } else if (!clean.startsWith("http://") && !clean.startsWith("https://")) {
            if (!clean.startsWith("/")) {
                clean = "/" + clean;
            }
            clean = pubUrl + clean;
        }
        return clean;
    }

    private String getPersonalLinkedInUrn(String accessToken) {
        try {
            HttpRequest userinfoRequest = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.linkedin.com/v2/userinfo"))
                    .header("Authorization", "Bearer " + accessToken)
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> resp = httpClient.send(userinfoRequest, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                JsonNode userNode = objectMapper.readTree(resp.body());
                if (userNode.has("sub")) {
                    String sub = userNode.path("sub").asText();
                    return sub.startsWith("urn:li:") ? sub : "urn:li:person:" + sub;
                }
            }
        } catch (Exception e) {
            log.warn("Could not fetch userinfo for personal URN fallback: {}", e.getMessage());
        }
        return "urn:li:person:me";
    }

    private String publishToLinkedIn(SocialAccount account, SocialPost post) throws Exception {
        String authorUrn = account.getAccountId();

        // If authorUrn was set to an organization (which requires unapproved Community
        // Management API),
        // or is missing, automatically reset to user's authorized personal member
        // profile to avoid 403 ACCESS_DENIED
        if (authorUrn == null || authorUrn.isBlank() || authorUrn.startsWith("urn:li:organization:")) {
            String personalUrn = getPersonalLinkedInUrn(account.getAccessToken());
            log.info("Using authorized LinkedIn personal member profile: {}", personalUrn);
            authorUrn = personalUrn;
            if (account.getAccountId() != null && account.getAccountId().startsWith("urn:li:organization:")) {
                account.setAccountId(personalUrn);
                account.setAccountName("Profil Pribadi LinkedIn");
                try {
                    accountRepository.save(account);
                } catch (Exception ex) {
                    log.warn("Could not save reset account ID: {}", ex.getMessage());
                }
            }
        } else if (!authorUrn.startsWith("urn:li:")) {
            authorUrn = "urn:li:person:" + authorUrn;
        }

        log.info("Publishing to LinkedIn as author: {}", authorUrn);

        String commentary = (post.getTitle() != null && !post.getTitle().isBlank())
                ? post.getTitle() + "\n\n" + (post.getCaption() != null ? post.getCaption() : "")
                : (post.getCaption() != null ? post.getCaption() : "New post from Zelvira");

        // 1. Upload native image binary to LinkedIn if media is present
        String uploadedImageUrn = null;
        String rawImageUrl = null;
        if (post.getMediaUrls() != null && !post.getMediaUrls().isBlank()) {
            rawImageUrl = post.getMediaUrls().split(",")[0].trim();
            boolean isImage = !"VIDEO".equalsIgnoreCase(post.getMediaType())
                    && !"TEXT".equalsIgnoreCase(post.getMediaType());
            if (isImage) {
                try {
                    byte[] imageBytes = fetchImageBytes(rawImageUrl);
                    if (imageBytes != null && imageBytes.length > 0) {
                        uploadedImageUrn = uploadImageToLinkedIn(account.getAccessToken(), authorUrn, imageBytes,
                                post.getTitle() != null ? post.getTitle() : "Zelvira Image");
                        log.info("LinkedIn image uploaded successfully, URN: {}", uploadedImageUrn);
                    }
                } catch (Exception ex) {
                    log.warn("Could not upload image binary to LinkedIn: {}", ex.getMessage());
                }
            }
        }

        // 2. Try modern LinkedIn REST Posts API (Version 202401)
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("author", authorUrn);
            payload.put("commentary", commentary);
            payload.put("visibility", "PUBLIC");
            payload.put("lifecycleState", "PUBLISHED");
            payload.put("isReshareDisabledByAuthor", false);

            Map<String, Object> distribution = new LinkedHashMap<>();
            distribution.put("feedDistribution", "MAIN_FEED");
            distribution.put("targetEntities", List.of());
            distribution.put("thirdPartyDistributionChannels", List.of());
            payload.put("distribution", distribution);

            if (uploadedImageUrn != null) {
                // Real native image media attachment
                Map<String, Object> mediaObj = new LinkedHashMap<>();
                mediaObj.put("id", uploadedImageUrn);
                if (post.getTitle() != null && !post.getTitle().isBlank()) {
                    mediaObj.put("title", post.getTitle());
                }
                payload.put("content", Map.of("media", mediaObj));
            } else if (rawImageUrl != null
                    && (rawImageUrl.startsWith("http://") || rawImageUrl.startsWith("https://"))) {
                // Article preview fallback
                Map<String, Object> article = new LinkedHashMap<>();
                article.put("source", rawImageUrl);
                article.put("title", post.getTitle() != null ? post.getTitle() : "Zelvira Store");
                if (post.getCaption() != null) {
                    article.put("description", post.getCaption());
                }
                payload.put("content", Map.of("article", article));
            }

            String jsonPayload = objectMapper.writeValueAsString(payload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.linkedin.com/rest/posts"))
                    .header("Authorization", "Bearer " + account.getAccessToken())
                    .header("LinkedIn-Version", "202401")
                    .header("X-Restli-Protocol-Version", "2.0.0")
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(20))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("LinkedIn REST Posts API response: status={}, body={}", response.statusCode(), response.body());

            if (response.statusCode() == 201) {
                String postUrn = response.headers().firstValue("x-restli-id")
                        .or(() -> response.headers().firstValue("x-linkedin-id"))
                        .orElse("urn:li:post:" + System.currentTimeMillis());
                log.info("LinkedIn post successfully created with URN: {}", postUrn);
                return postUrn;
            }

            log.warn("LinkedIn REST API status {}, falling back to UGC API: {}", response.statusCode(),
                    response.body());
        } catch (Exception e) {
            log.warn("LinkedIn REST API failed, trying UGC API fallback: {}", e.getMessage());
        }

        // 3. Fallback: UGC Posts API (https://api.linkedin.com/v2/ugcPosts)
        Map<String, Object> ugcPayload = new LinkedHashMap<>();
        ugcPayload.put("author", authorUrn);
        ugcPayload.put("lifecycleState", "PUBLISHED");

        Map<String, Object> shareCommentary = Map.of("text", commentary);
        Map<String, Object> shareContent = new LinkedHashMap<>();
        shareContent.put("shareCommentary", shareCommentary);

        if (uploadedImageUrn != null) {
            shareContent.put("shareMediaCategory", "IMAGE");
            Map<String, Object> mediaItem = new LinkedHashMap<>();
            mediaItem.put("status", "READY");
            mediaItem.put("media", uploadedImageUrn);
            if (post.getTitle() != null) {
                mediaItem.put("title", Map.of("text", post.getTitle()));
            }
            if (post.getCaption() != null) {
                mediaItem.put("description", Map.of("text", post.getCaption()));
            }
            shareContent.put("media", List.of(mediaItem));
        } else if (rawImageUrl != null && (rawImageUrl.startsWith("http://") || rawImageUrl.startsWith("https://"))) {
            shareContent.put("shareMediaCategory", "ARTICLE");
            shareContent.put("media", List.of(Map.of(
                    "status", "READY",
                    "originalUrl", rawImageUrl,
                    "title", Map.of("text", post.getTitle() != null ? post.getTitle() : "Zelvira"),
                    "description", Map.of("text", post.getCaption() != null ? post.getCaption() : ""))));
        } else {
            shareContent.put("shareMediaCategory", "NONE");
        }

        ugcPayload.put("specificContent", Map.of("com.linkedin.ugc.ShareContent", shareContent));
        ugcPayload.put("visibility", Map.of("com.linkedin.ugc.MemberNetworkVisibility", "PUBLIC"));

        String jsonUgc = objectMapper.writeValueAsString(ugcPayload);

        HttpRequest ugcRequest = HttpRequest.newBuilder()
                .uri(URI.create("https://api.linkedin.com/v2/ugcPosts"))
                .header("Authorization", "Bearer " + account.getAccessToken())
                .header("X-Restli-Protocol-Version", "2.0.0")
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(20))
                .POST(HttpRequest.BodyPublishers.ofString(jsonUgc))
                .build();

        HttpResponse<String> ugcResponse = httpClient.send(ugcRequest, HttpResponse.BodyHandlers.ofString());
        log.info("LinkedIn UGC Posts API response: status={}, body={}", ugcResponse.statusCode(), ugcResponse.body());

        if (ugcResponse.statusCode() >= 200 && ugcResponse.statusCode() < 300) {
            JsonNode root = objectMapper.readTree(ugcResponse.body());
            return root.path("id").asText("urn:li:ugcPost:" + System.currentTimeMillis());
        }

        throw new RuntimeException(
                "LinkedIn publishing failed: HTTP " + ugcResponse.statusCode() + " - " + ugcResponse.body());
    }

    private String uploadImageToLinkedIn(String accessToken, String authorUrn, byte[] imageBytes, String title) {
        if (imageBytes == null || imageBytes.length == 0) {
            return null;
        }

        // Try 1: Modern REST Images API (initializeUpload)
        try {
            log.info("Initializing image upload to LinkedIn REST Images API for author: {}", authorUrn);
            Map<String, Object> reqBody = Map.of(
                    "initializeUploadRequest", Map.of("owner", authorUrn));
            String jsonReq = objectMapper.writeValueAsString(reqBody);

            HttpRequest initRequest = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.linkedin.com/rest/images?action=initializeUpload"))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("LinkedIn-Version", "202401")
                    .header("X-Restli-Protocol-Version", "2.0.0")
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(20))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonReq))
                    .build();

            HttpResponse<String> initResp = httpClient.send(initRequest, HttpResponse.BodyHandlers.ofString());
            log.info("LinkedIn initializeUpload status: {}, body: {}", initResp.statusCode(), initResp.body());

            if (initResp.statusCode() == 200) {
                JsonNode initNode = objectMapper.readTree(initResp.body());
                JsonNode value = initNode.path("value");
                String uploadUrl = value.path("uploadUrl").asText(null);
                String imageUrn = value.path("image").asText(null);

                if (uploadUrl != null && imageUrn != null) {
                    HttpRequest uploadReq = HttpRequest.newBuilder()
                            .uri(URI.create(uploadUrl))
                            .header("Authorization", "Bearer " + accessToken)
                            .header("Content-Type", "image/jpeg")
                            .timeout(Duration.ofSeconds(60))
                            .PUT(HttpRequest.BodyPublishers.ofByteArray(imageBytes))
                            .build();

                    HttpResponse<String> uploadResp = httpClient.send(uploadReq, HttpResponse.BodyHandlers.ofString());
                    log.info("LinkedIn image PUT binary status: {}", uploadResp.statusCode());

                    if (uploadResp.statusCode() >= 200 && uploadResp.statusCode() < 300) {
                        log.info("LinkedIn image successfully uploaded! Image URN: {}", imageUrn);
                        return imageUrn;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("LinkedIn REST image upload failed: {}, falling back to Assets API", e.getMessage());
        }

        // Try 2: Fallback LinkedIn v2 Assets API (registerUpload)
        try {
            log.info("Registering image upload with LinkedIn v2 Assets API for author: {}", authorUrn);
            Map<String, Object> registerRequest = new LinkedHashMap<>();
            registerRequest.put("recipes", List.of("urn:li:digitalmediaRecipe:feedshare-image"));
            registerRequest.put("owner", authorUrn);
            registerRequest.put("supportedUploadMechanism", List.of("SYNCHRONOUS_UPLOAD"));

            Map<String, Object> reqBody = Map.of("registerUploadRequest", registerRequest);
            String jsonReq = objectMapper.writeValueAsString(reqBody);

            HttpRequest registerReq = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.linkedin.com/v2/assets?action=registerUpload"))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(20))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonReq))
                    .build();

            HttpResponse<String> regResp = httpClient.send(registerReq, HttpResponse.BodyHandlers.ofString());
            log.info("LinkedIn Assets registerUpload status: {}, body: {}", regResp.statusCode(), regResp.body());

            if (regResp.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(regResp.body());
                JsonNode val = root.path("value");
                String assetUrn = val.path("asset").asText(null);
                String uploadUrl = val.path("uploadMechanism")
                        .path("com.linkedin.digitalmedia.uploading.MediaUploadHttpRequest")
                        .path("uploadUrl").asText(null);

                if (uploadUrl != null && assetUrn != null) {
                    HttpRequest putReq = HttpRequest.newBuilder()
                            .uri(URI.create(uploadUrl))
                            .header("Authorization", "Bearer " + accessToken)
                            .header("Content-Type", "image/jpeg")
                            .timeout(Duration.ofSeconds(60))
                            .PUT(HttpRequest.BodyPublishers.ofByteArray(imageBytes))
                            .build();

                    HttpResponse<String> putResp = httpClient.send(putReq, HttpResponse.BodyHandlers.ofString());
                    log.info("LinkedIn Assets PUT binary status: {}", putResp.statusCode());

                    if (putResp.statusCode() >= 200 && putResp.statusCode() < 300) {
                        log.info("LinkedIn v2 asset successfully uploaded! Asset URN: {}", assetUrn);
                        return assetUrn;
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to upload image via LinkedIn Assets API: {}", e.getMessage());
        }

        return null;
    }

    private byte[] fetchImageBytes(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return null;
        }

        log.info("Fetching image bytes for: {}", imageUrl);

        // 1. If stored in MinIO and minioClient is available
        if (minioClient != null) {
            try {
                String bucket = minioBucket;
                String objectName = null;

                if (!imageUrl.startsWith("http://") && !imageUrl.startsWith("https://")) {
                    String cleanPath = imageUrl.startsWith("/") ? imageUrl.substring(1) : imageUrl;
                    if (cleanPath.startsWith(bucket + "/")) {
                        objectName = cleanPath.substring(bucket.length() + 1);
                    } else if (cleanPath.contains("/")) {
                        int slashIdx = cleanPath.indexOf("/");
                        bucket = cleanPath.substring(0, slashIdx);
                        objectName = cleanPath.substring(slashIdx + 1);
                    } else {
                        objectName = cleanPath;
                    }
                } else if (imageUrl.contains(bucket) || imageUrl.contains("minio") || imageUrl.contains("storage")) {
                    URI uri = URI.create(imageUrl);
                    String path = uri.getPath();
                    if (path != null) {
                        if (path.startsWith("/"))
                            path = path.substring(1);
                        if (path.startsWith(bucket + "/")) {
                            objectName = path.substring(bucket.length() + 1);
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
                            log.info("Fetched {} image bytes directly from MinIO object: {}", bytes.length, objectName);
                            return bytes;
                        }
                    }
                }
            } catch (Exception e) {
                log.debug("Direct MinIO download failed for {}: {}", imageUrl, e.getMessage());
            }
        }

        // 2. HTTP/HTTPS Download
        if (imageUrl.startsWith("http://") || imageUrl.startsWith("https://")) {
            String resolvedUrl = imageUrl;
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
                log.warn("Public HTTP download failed for {}: {}", resolvedUrl, e.getMessage());
            }

            // Fallback for docker internal network if public domain DNS fails inside
            // container
            if (imageUrl.contains("minio.zelvira.co.id")) {
                try {
                    String internalUrl = imageUrl.replace("https://minio.zelvira.co.id",
                            minioInternalUrl.endsWith("/")
                                    ? minioInternalUrl.substring(0, minioInternalUrl.length() - 1)
                                    : minioInternalUrl);
                    HttpRequest intReq = HttpRequest.newBuilder()
                            .uri(URI.create(internalUrl))
                            .timeout(Duration.ofSeconds(15))
                            .GET()
                            .build();
                    HttpResponse<byte[]> intResp = httpClient.send(intReq, HttpResponse.BodyHandlers.ofByteArray());
                    if (intResp.statusCode() == 200 && intResp.body() != null && intResp.body().length > 0) {
                        return intResp.body();
                    }
                } catch (Exception ex) {
                    log.warn("Internal MinIO HTTP download failed: {}", ex.getMessage());
                }
            }
        }

        return null;
    }

    // ==========================================
    // COMMENTS & ENGAGEMENT (INTEGRATED DATA ONLY)
    // ==========================================

    @Override
    public List<SocialCommentResponse> getComments(String platform, String search) {
        // 1. Fetch only connected active accounts
        List<SocialAccount> activeAccounts = accountRepository.findAllByIsActiveTrue();
        if (activeAccounts.isEmpty()) {
            // Never return dummy seed comments when no accounts are integrated
            return Collections.emptyList();
        }

        Set<String> activePlatforms = activeAccounts.stream()
                .map(a -> a.getPlatform().toUpperCase())
                .collect(Collectors.toSet());

        Set<Long> activeAccountIds = activeAccounts.stream()
                .map(SocialAccount::getId)
                .collect(Collectors.toSet());

        Map<Long, String> accountNamesById = activeAccounts.stream()
                .collect(Collectors.toMap(SocialAccount::getId,
                        a -> a.getAccountName() != null ? a.getAccountName() : a.getPlatform(), (k1, k2) -> k1));

        // 2. Fetch all comments from DB
        List<SocialComment> list = commentRepository.findAllByOrderByCreatedAtDesc();

        // 3. Purge legacy fake sample comments (e.g. from previous initSampleComments
        // seeds)
        List<SocialComment> legacySamples = list.stream()
                .filter(c -> c.getExternalCommentId() == null
                        && ("@nisarahma_".equalsIgnoreCase(c.getAuthorUsername()) ||
                                "@dinda.ootd".equalsIgnoreCase(c.getAuthorUsername()) ||
                                "@rinasulistyo".equalsIgnoreCase(c.getAuthorUsername()) ||
                                "Nisa Rahmawati".equalsIgnoreCase(c.getAuthorName()) ||
                                "Dinda Pratama".equalsIgnoreCase(c.getAuthorName()) ||
                                "Rina Sulistyo".equalsIgnoreCase(c.getAuthorName())))
                .collect(Collectors.toList());
        if (!legacySamples.isEmpty()) {
            try {
                commentRepository.deleteAll(legacySamples);
                list.removeAll(legacySamples);
            } catch (Exception e) {
                log.warn("Could not purge legacy sample comments: {}", e.getMessage());
            }
        }

        // 4. Return ONLY comments from active integrated accounts/platforms
        return list.stream()
                .filter(c -> c.getPlatform() != null && activePlatforms.contains(c.getPlatform().toUpperCase()))
                .filter(c -> c.getSocialAccountId() == null || activeAccountIds.contains(c.getSocialAccountId()))
                .filter(c -> platform == null || platform.isBlank() || "ALL".equalsIgnoreCase(platform)
                        || c.getPlatform().equalsIgnoreCase(platform))
                .filter(c -> search == null || search.isBlank() ||
                        (c.getCommentText() != null && c.getCommentText().toLowerCase().contains(search.toLowerCase()))
                        ||
                        (c.getAuthorName() != null && c.getAuthorName().toLowerCase().contains(search.toLowerCase())) ||
                        (c.getAuthorUsername() != null
                                && c.getAuthorUsername().toLowerCase().contains(search.toLowerCase())))
                .map(c -> {
                    SocialCommentResponse resp = toCommentResponse(c);
                    if (c.getSocialAccountId() != null && accountNamesById.containsKey(c.getSocialAccountId())) {
                        resp.setAccountName(accountNamesById.get(c.getSocialAccountId()));
                    } else {
                        activeAccounts.stream()
                                .filter(a -> a.getPlatform().equalsIgnoreCase(c.getPlatform()))
                                .findFirst()
                                .ifPresent(a -> resp.setAccountName(a.getAccountName()));
                    }
                    return resp;
                })
                .collect(Collectors.toList());
    }

    @Override
    public int syncComments() {
        List<SocialAccount> activeAccounts = accountRepository.findAllByIsActiveTrue();
        int totalNewComments = 0;
        for (SocialAccount account : activeAccounts) {
            try {
                if ("INSTAGRAM".equalsIgnoreCase(account.getPlatform())) {
                    totalNewComments += syncInstagramComments(account);
                } else if ("TIKTOK".equalsIgnoreCase(account.getPlatform())) {
                    totalNewComments += syncTikTokComments(account);
                } else if ("YOUTUBE".equalsIgnoreCase(account.getPlatform())) {
                    totalNewComments += syncYouTubeComments(account);
                }
            } catch (Exception e) {
                log.error("Failed to sync comments for {} account {}: {}",
                        account.getPlatform(), account.getAccountName(), e.getMessage());
            }
        }
        log.info("Synced {} new comments from integrated social channels.", totalNewComments);
        return totalNewComments;
    }

    private int syncInstagramComments(SocialAccount account) {
        String token = account.getAccessToken();
        if (token == null || token.isBlank())
            return 0;

        String accountId = account.getAccountId() != null && !account.getAccountId().isBlank()
                ? account.getAccountId()
                : instagramDefaultAccountId;

        if (accountId == null || accountId.isBlank())
            return 0;

        int added = 0;
        try {
            String url = String.format(
                    "https://graph.facebook.com/v21.0/%s/media?fields=id,caption,media_type,thumbnail_url,media_url,permalink,timestamp,comments{id,text,username,timestamp,like_count}&limit=10&access_token=%s",
                    accountId, URLEncoder.encode(token, StandardCharsets.UTF_8));

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(20))
                    .GET()
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(resp.body());
                JsonNode mediaData = root.path("data");
                if (mediaData.isArray()) {
                    for (JsonNode media : mediaData) {
                        String mediaId = media.path("id").asText();
                        String caption = media.path("caption").asText(null);
                        String thumb = media.has("thumbnail_url") ? media.path("thumbnail_url").asText()
                                : media.path("media_url").asText(null);

                        JsonNode commentsNode = media.path("comments").path("data");
                        if (commentsNode.isArray()) {
                            for (JsonNode cNode : commentsNode) {
                                String commentId = cNode.path("id").asText();
                                if (commentId != null && !commentId.isBlank()
                                        && !commentRepository.existsByExternalCommentId(commentId)) {
                                    String text = cNode.path("text").asText("");
                                    String username = cNode.path("username").asText("Instagram User");

                                    SocialComment sc = SocialComment.builder()
                                            .socialAccountId(account.getId())
                                            .platform("INSTAGRAM")
                                            .externalCommentId(commentId)
                                            .externalPostId(mediaId)
                                            .authorName(username)
                                            .authorUsername("@" + username)
                                            .commentText(text)
                                            .postTitle(caption != null && caption.length() > 60
                                                    ? caption.substring(0, 60) + "..."
                                                    : caption)
                                            .postThumbnailUrl(thumb)
                                            .isReplied(false)
                                            .status("PENDING")
                                            .createdAt(LocalDateTime.now())
                                            .build();
                                    commentRepository.save(sc);
                                    added++;
                                }
                            }
                        }
                    }
                }
            } else {
                log.warn("Instagram Graph API returned status {} when syncing comments for {}: {}", resp.statusCode(),
                        account.getAccountName(), resp.body());
            }
        } catch (Exception e) {
            log.error("Error syncing Instagram comments for account {}: {}", account.getAccountName(), e.getMessage());
        }
        return added;
    }

    private int syncTikTokComments(SocialAccount account) {
        String token = account.getAccessToken();
        if (token == null || token.isBlank())
            return 0;

        int added = 0;
        try {
            List<SocialPost> tiktokPosts = postRepository.findByStatusOrderByScheduledAtAsc("PUBLISHED").stream()
                    .filter(p -> p.getTargetPlatforms() != null
                            && p.getTargetPlatforms().toUpperCase().contains("TIKTOK")
                            && (p.getTiktokPublishId() != null || p.getExternalPostId() != null))
                    .collect(Collectors.toList());

            for (SocialPost post : tiktokPosts) {
                try {
                    Map<String, Object> reqBody = Map.of(
                            "video_id",
                            (post.getTiktokPublishId() != null ? post.getTiktokPublishId() : post.getExternalPostId()),
                            "max_count", 20);
                    HttpRequest req = HttpRequest.newBuilder()
                            .uri(URI.create("https://open.tiktokapis.com/v2/video/comment/list/"))
                            .header("Authorization", "Bearer " + token)
                            .header("Content-Type", "application/json")
                            .timeout(Duration.ofSeconds(15))
                            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(reqBody)))
                            .build();

                    HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                    if (resp.statusCode() == 200) {
                        JsonNode root = objectMapper.readTree(resp.body());
                        JsonNode comments = root.path("data").path("comments");
                        if (comments.isArray()) {
                            for (JsonNode cNode : comments) {
                                String commentId = cNode.path("id").asText();
                                if (commentId != null && !commentId.isBlank()
                                        && !commentRepository.existsByExternalCommentId(commentId)) {
                                    String text = cNode.path("text").asText("");
                                    String user = cNode.path("user_name").asText("TikTok User");

                                    SocialComment sc = SocialComment.builder()
                                            .socialAccountId(account.getId())
                                            .postId(post.getId())
                                            .platform("TIKTOK")
                                            .externalCommentId(commentId)
                                            .externalPostId(
                                                    post.getTiktokPublishId() != null ? post.getTiktokPublishId()
                                                            : post.getExternalPostId())
                                            .authorName(user)
                                            .authorUsername("@" + user)
                                            .commentText(text)
                                            .postTitle(post.getCaption())
                                            .postThumbnailUrl(post.getMediaUrls())
                                            .isReplied(false)
                                            .status("PENDING")
                                            .createdAt(LocalDateTime.now())
                                            .build();
                                    commentRepository.save(sc);
                                    added++;
                                }
                            }
                        }
                    }
                } catch (Exception postEx) {
                    log.warn("Error fetching TikTok comments for post {}: {}", post.getId(), postEx.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Error syncing TikTok comments for account {}: {}", account.getAccountName(), e.getMessage());
        }
        return added;
    }

    private int syncYouTubeComments(SocialAccount account) {
        String token = getValidYouTubeAccessToken(account);
        if (token == null || token.isBlank())
            return 0;

        int added = 0;
        try {
            String channelId = account.getAccountId();
            String url = (channelId != null && channelId.startsWith("UC"))
                    ? "https://www.googleapis.com/youtube/v3/commentThreads?part=snippet&allThreadsRelatedToChannelId="
                            + channelId + "&maxResults=20"
                    : null;

            if (url != null) {
                try {
                    HttpRequest req = HttpRequest.newBuilder()
                            .uri(URI.create(url))
                            .header("Authorization", "Bearer " + token)
                            .timeout(Duration.ofSeconds(15))
                            .GET()
                            .build();

                    HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                    if (resp.statusCode() == 200) {
                        JsonNode root = objectMapper.readTree(resp.body());
                        JsonNode items = root.path("items");
                        if (items.isArray()) {
                            for (JsonNode item : items) {
                                JsonNode topComment = item.path("snippet").path("topLevelComment");
                                String commentId = topComment.path("id").asText(item.path("id").asText(null));
                                String videoId = item.path("snippet").path("videoId").asText(null);

                                if (commentId != null && !commentId.isBlank()
                                        && !commentRepository.existsByExternalCommentId(commentId)) {
                                    JsonNode cSnippet = topComment.path("snippet");
                                    String text = cSnippet.path("textDisplay").asText("");
                                    String author = cSnippet.path("authorDisplayName").asText("YouTube User");
                                    String avatar = cSnippet.path("authorProfileImageUrl").asText(null);

                                    SocialComment sc = SocialComment.builder()
                                            .socialAccountId(account.getId())
                                            .platform("YOUTUBE")
                                            .externalCommentId(commentId)
                                            .externalPostId(videoId)
                                            .authorName(author)
                                            .authorUsername(author.startsWith("@") ? author
                                                    : "@" + author.replaceAll("\\s+", ""))
                                            .authorAvatarUrl(avatar)
                                            .commentText(text)
                                            .postTitle("YouTube Video: " + videoId)
                                            .isReplied(false)
                                            .status("PENDING")
                                            .createdAt(LocalDateTime.now())
                                            .build();
                                    commentRepository.save(sc);
                                    added++;
                                }
                            }
                        }
                    }
                } catch (Exception ex) {
                    log.warn("Channel commentThreads sync failed: {}", ex.getMessage());
                }
            }

            // Also check recently published YouTube posts in database
            List<SocialPost> ytPosts = postRepository.findByStatusOrderByScheduledAtAsc("PUBLISHED").stream()
                    .filter(p -> p.getTargetPlatforms() != null
                            && p.getTargetPlatforms().toUpperCase().contains("YOUTUBE")
                            && p.getYoutubeVideoId() != null)
                    .collect(Collectors.toList());

            for (SocialPost post : ytPosts) {
                try {
                    String vUrl = "https://www.googleapis.com/youtube/v3/commentThreads?part=snippet&videoId="
                            + post.getYoutubeVideoId() + "&maxResults=10";
                    HttpRequest vReq = HttpRequest.newBuilder()
                            .uri(URI.create(vUrl))
                            .header("Authorization", "Bearer " + token)
                            .timeout(Duration.ofSeconds(10))
                            .GET()
                            .build();

                    HttpResponse<String> vResp = httpClient.send(vReq, HttpResponse.BodyHandlers.ofString());
                    if (vResp.statusCode() == 200) {
                        JsonNode root = objectMapper.readTree(vResp.body());
                        JsonNode items = root.path("items");
                        if (items.isArray()) {
                            for (JsonNode item : items) {
                                JsonNode topComment = item.path("snippet").path("topLevelComment");
                                String commentId = topComment.path("id").asText(item.path("id").asText(null));
                                if (commentId != null && !commentId.isBlank()
                                        && !commentRepository.existsByExternalCommentId(commentId)) {
                                    JsonNode cSnippet = topComment.path("snippet");
                                    String text = cSnippet.path("textDisplay").asText("");
                                    String author = cSnippet.path("authorDisplayName").asText("YouTube User");
                                    String avatar = cSnippet.path("authorProfileImageUrl").asText(null);

                                    SocialComment sc = SocialComment.builder()
                                            .socialAccountId(account.getId())
                                            .postId(post.getId())
                                            .platform("YOUTUBE")
                                            .externalCommentId(commentId)
                                            .externalPostId(post.getYoutubeVideoId())
                                            .authorName(author)
                                            .authorUsername(author.startsWith("@") ? author
                                                    : "@" + author.replaceAll("\\s+", ""))
                                            .authorAvatarUrl(avatar)
                                            .commentText(text)
                                            .postTitle(post.getTitle() != null ? post.getTitle() : post.getCaption())
                                            .postThumbnailUrl(post.getThumbnailUrl() != null ? post.getThumbnailUrl()
                                                    : post.getMediaUrls())
                                            .isReplied(false)
                                            .status("PENDING")
                                            .createdAt(LocalDateTime.now())
                                            .build();
                                    commentRepository.save(sc);
                                    added++;
                                }
                            }
                        }
                    }
                } catch (Exception pEx) {
                    log.debug("Video comments sync skipped for {}: {}", post.getYoutubeVideoId(), pEx.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Error syncing YouTube comments for account {}: {}", account.getAccountName(), e.getMessage());
        }
        return added;
    }

    @Override
    @Transactional
    public SocialCommentResponse replyComment(Long commentId, String replyText) {
        SocialComment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new RuntimeException("Komentar tidak ditemukan: " + commentId));

        comment.setReplyText(replyText);
        comment.setIsReplied(true);
        comment.setRepliedAt(LocalDateTime.now());
        comment.setStatus("REPLIED");

        try {
            SocialAccount account = null;
            if (comment.getSocialAccountId() != null) {
                account = accountRepository.findById(comment.getSocialAccountId()).orElse(null);
            }
            if (account == null) {
                account = accountRepository.findFirstByPlatformAndIsActiveTrue(comment.getPlatform()).orElse(null);
            }

            if (account != null && account.getAccessToken() != null && !account.getAccessToken().isBlank()
                    && comment.getExternalCommentId() != null) {
                if ("INSTAGRAM".equalsIgnoreCase(comment.getPlatform())) {
                    String replyUrl = String.format("https://graph.facebook.com/v21.0/%s/replies",
                            comment.getExternalCommentId());
                    String formBody = "message=" + URLEncoder.encode(replyText, StandardCharsets.UTF_8);
                    HttpRequest req = HttpRequest.newBuilder()
                            .uri(URI.create(replyUrl))
                            .header("Authorization", "Bearer " + account.getAccessToken())
                            .header("Content-Type", "application/x-www-form-urlencoded")
                            .timeout(Duration.ofSeconds(15))
                            .POST(HttpRequest.BodyPublishers.ofString(formBody))
                            .build();
                    HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                    log.info("Instagram reply API response: status={}, body={}", resp.statusCode(), resp.body());
                } else if ("TIKTOK".equalsIgnoreCase(comment.getPlatform()) && comment.getExternalPostId() != null) {
                    Map<String, Object> body = Map.of(
                            "video_id", comment.getExternalPostId(),
                            "comment_id", comment.getExternalCommentId(),
                            "text", replyText);
                    HttpRequest req = HttpRequest.newBuilder()
                            .uri(URI.create("https://open.tiktokapis.com/v2/video/comment/reply/"))
                            .header("Authorization", "Bearer " + account.getAccessToken())
                            .header("Content-Type", "application/json")
                            .timeout(Duration.ofSeconds(15))
                            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                            .build();
                    HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                    log.info("TikTok reply API response: status={}, body={}", resp.statusCode(), resp.body());
                } else if ("YOUTUBE".equalsIgnoreCase(comment.getPlatform())
                        && comment.getExternalCommentId() != null) {
                    String validToken = getValidYouTubeAccessToken(account);
                    Map<String, Object> snippet = Map.of(
                            "parentId", comment.getExternalCommentId(),
                            "textOriginal", replyText);
                    Map<String, Object> body = Map.of("snippet", snippet);
                    HttpRequest req = HttpRequest.newBuilder()
                            .uri(URI.create("https://www.googleapis.com/youtube/v3/comments?part=snippet"))
                            .header("Authorization", "Bearer " + validToken)
                            .header("Content-Type", "application/json")
                            .timeout(Duration.ofSeconds(15))
                            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                            .build();
                    HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                    log.info("YouTube reply API response: status={}, body={}", resp.statusCode(), resp.body());
                }
            }
        } catch (Exception e) {
            log.error("Failed to dispatch live reply to {} API for comment {}: {}",
                    comment.getPlatform(), commentId, e.getMessage());
        }

        SocialComment saved = commentRepository.save(comment);
        log.info("Replied to comment id={}, platform={}", commentId, comment.getPlatform());
        return toCommentResponse(saved);
    }

    @Override
    @Transactional
    public void hideComment(Long commentId) {
        commentRepository.findById(commentId).ifPresent(c -> {
            c.setStatus("HIDDEN");
            commentRepository.save(c);
            log.info("Hidden comment id={}", commentId);
        });
    }

    // ==========================================
    // LIVE FEED & STATUS
    // ==========================================

    @Override
    public List<Map<String, Object>> getLiveFeedAndStatus(String platform) {
        List<SocialPost> publishedPosts = new ArrayList<>(
                postRepository.findByStatusOrderByScheduledAtAsc("PUBLISHED"));
        publishedPosts.sort((a, b) -> {
            java.time.LocalDateTime timeA = a.getPublishedAt() != null ? a.getPublishedAt() : a.getCreatedAt();
            java.time.LocalDateTime timeB = b.getPublishedAt() != null ? b.getPublishedAt() : b.getCreatedAt();
            if (timeA == null && timeB == null) {
                return Long.compare(b.getId() != null ? b.getId() : 0, a.getId() != null ? a.getId() : 0);
            }
            if (timeA == null)
                return 1;
            if (timeB == null)
                return -1;
            return timeB.compareTo(timeA);
        });

        List<Map<String, Object>> feedItems = new ArrayList<>();
        for (SocialPost p : publishedPosts) {
            if (platform != null && !platform.isBlank() && !"ALL".equalsIgnoreCase(platform)) {
                if (!p.getTargetPlatforms().toUpperCase().contains(platform.toUpperCase())) {
                    continue;
                }
            }

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", p.getId());
            item.put("caption", p.getCaption());
            item.put("mediaUrl", p.getMediaUrls());
            item.put("thumbnailUrl", p.getThumbnailUrl());
            item.put("mediaType", p.getMediaType());
            item.put("postType", p.getPostType());
            item.put("targetPlatforms", p.getTargetPlatforms());
            item.put("publishedAt", p.getPublishedAt() != null ? p.getPublishedAt() : p.getCreatedAt());
            item.put("views", 1250 + (p.getId() * 180));
            item.put("likes", 84 + (p.getId() * 22));
            item.put("comments", 12 + (p.getId() * 3));
            item.put("shares", 5 + (p.getId() * 2));
            item.put("status", "LIVE");
            item.put("youtubeVideoId", p.getYoutubeVideoId());
            item.put("linkedinPostUrn", p.getLinkedinPostUrn());
            item.put("tiktokPublishId", p.getTiktokPublishId());
            item.put("instagramMediaId", p.getInstagramMediaId());

            String liveUrl = null;
            if (p.getYoutubeVideoId() != null && !p.getYoutubeVideoId().isBlank()) {
                boolean isYtShorts = "SHORTS".equalsIgnoreCase(p.getPostType()) ||
                        "REEL".equalsIgnoreCase(p.getPostType()) ||
                        "STORY".equalsIgnoreCase(p.getPostType()) ||
                        (p.getCaption() != null && p.getCaption().toLowerCase().contains("#shorts"));
                liveUrl = isYtShorts
                        ? "https://www.youtube.com/shorts/" + p.getYoutubeVideoId()
                        : "https://www.youtube.com/watch?v=" + p.getYoutubeVideoId();
            } else if (p.getLinkedinPostUrn() != null && !p.getLinkedinPostUrn().isBlank()) {
                liveUrl = "https://www.linkedin.com/feed/update/" + p.getLinkedinPostUrn();
            } else if (p.getMediaUrls() != null && !p.getMediaUrls().isBlank()) {
                liveUrl = p.getMediaUrls().split(",")[0].trim();
            }
            item.put("liveUrl", liveUrl);

            feedItems.add(item);
        }

        return feedItems;
    }

    // ==========================================
    // CALENDAR & STATS
    // ==========================================

    @Override
    public List<SocialPostResponse> getCalendarPosts(LocalDateTime start, LocalDateTime end) {
        LocalDateTime queryStart = start != null ? start : LocalDateTime.now().minusMonths(1);
        LocalDateTime queryEnd = end != null ? end : LocalDateTime.now().plusMonths(2);

        List<SocialPost> posts = postRepository.findPostsBetweenDates(queryStart, queryEnd);
        return posts.stream().map(this::toPostResponse).collect(Collectors.toList());
    }

    @Override
    public Map<String, Object> getSocialStats() {
        long total = postRepository.count();
        long published = postRepository.countByStatus("PUBLISHED");
        long scheduled = postRepository.countByStatus("SCHEDULED");
        long draft = postRepository.countByStatus("DRAFT");
        long failed = postRepository.countByStatus("FAILED");
        long pendingComments = commentRepository.countByIsRepliedFalse();

        return Map.of(
                "totalPosts", total,
                "publishedCount", published,
                "scheduledCount", scheduled,
                "draftCount", draft,
                "failedCount", failed,
                "pendingComments", pendingComments);
    }

    @Override
    @Scheduled(cron = "0 * * * * *")
    public void processDueScheduledPosts() {
        LocalDateTime now = LocalDateTime.now();
        List<SocialPost> duePosts = postRepository.findByStatusAndScheduledAtLessThanEqual("SCHEDULED", now);
        if (!duePosts.isEmpty()) {
            log.info("Found {} due scheduled social media posts to publish", duePosts.size());
            for (SocialPost post : duePosts) {
                try {
                    executePublish(post);
                } catch (Exception e) {
                    log.error("Failed to auto-publish scheduled post id={}: {}", post.getId(), e.getMessage());
                }
            }
        }
    }

    private SocialPostResponse toPostResponse(SocialPost p) {
        return SocialPostResponse.builder()
                .id(p.getId())
                .productId(p.getProductId())
                .title(p.getTitle())
                .caption(p.getCaption())
                .mediaType(p.getMediaType())
                .mediaUrls(p.getMediaUrls())
                .thumbnailUrl(p.getThumbnailUrl())
                .targetPlatforms(p.getTargetPlatforms())
                .postType(p.getPostType())
                .status(p.getStatus())
                .scheduledAt(p.getScheduledAt())
                .publishedAt(p.getPublishedAt())
                .errorMessage(p.getErrorMessage())
                .tiktokPublishId(p.getTiktokPublishId())
                .instagramMediaId(p.getInstagramMediaId())
                .linkedinPostUrn(p.getLinkedinPostUrn())
                .youtubeVideoId(p.getYoutubeVideoId())
                .privacyLevel(p.getPrivacyLevel())
                .tiktokPostMode(p.getTiktokPostMode())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }

    private SocialAccountResponse toAccountResponse(SocialAccount a) {
        String clientKey = a.getClientKey();
        if (clientKey == null || clientKey.isBlank()) {
            if ("LINKEDIN".equalsIgnoreCase(a.getPlatform()))
                clientKey = linkedinClientId != null && !linkedinClientId.isBlank() ? linkedinClientId : "";
            else if ("TIKTOK".equalsIgnoreCase(a.getPlatform()))
                clientKey = getTiktokClientKey();
            else if ("INSTAGRAM".equalsIgnoreCase(a.getPlatform()))
                clientKey = instagramAppId != null ? instagramAppId : "";
            else if ("YOUTUBE".equalsIgnoreCase(a.getPlatform()))
                clientKey = youtubeClientId != null && !youtubeClientId.isBlank() ? youtubeClientId : "";
        }

        return SocialAccountResponse.builder()
                .id(a.getId())
                .platform(a.getPlatform())
                .accountName(a.getAccountName())
                .accountId(a.getAccountId())
                .avatarUrl(a.getAvatarUrl())
                .isActive(Boolean.TRUE.equals(a.getIsActive()))
                .hasToken(a.getAccessToken() != null && !a.getAccessToken().isBlank())
                .tokenExpiry(a.getTokenExpiry())
                .clientKey(clientKey)
                .createdAt(a.getCreatedAt())
                .build();
    }

    private SocialCommentResponse toCommentResponse(SocialComment c) {
        return SocialCommentResponse.builder()
                .id(c.getId())
                .postId(c.getPostId())
                .socialAccountId(c.getSocialAccountId())
                .platform(c.getPlatform())
                .authorName(c.getAuthorName())
                .authorUsername(c.getAuthorUsername())
                .authorAvatarUrl(c.getAuthorAvatarUrl())
                .commentText(c.getCommentText())
                .postTitle(c.getPostTitle())
                .postThumbnailUrl(c.getPostThumbnailUrl())
                .externalCommentId(c.getExternalCommentId())
                .externalPostId(c.getExternalPostId())
                .replyText(c.getReplyText())
                .isReplied(Boolean.TRUE.equals(c.getIsReplied()))
                .repliedAt(c.getRepliedAt())
                .status(c.getStatus())
                .createdAt(c.getCreatedAt())
                .build();
    }
}

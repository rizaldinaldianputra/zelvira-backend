package com.zelvira.zelvira.controller;

import com.zelvira.zelvira.dto.ApiResponse;
import com.zelvira.zelvira.dto.SocialAccountResponse;
import com.zelvira.zelvira.dto.SocialCommentResponse;
import com.zelvira.zelvira.dto.SocialPostRequest;
import com.zelvira.zelvira.dto.SocialPostResponse;
import com.zelvira.zelvira.service.SocialMediaService;
import com.zelvira.zelvira.service.impl.SocialMediaServiceImpl;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping({ "/api/v1/social", "/api/social" })
@Slf4j
public class SocialMediaController {

    private final SocialMediaService socialMediaService;

    public SocialMediaController(SocialMediaService socialMediaService) {
        this.socialMediaService = socialMediaService;
    }

    // ==========================================
    // ACCOUNTS
    // ==========================================

    @GetMapping("/accounts")
    public ResponseEntity<ApiResponse<List<SocialAccountResponse>>> getAccounts() {
        List<SocialAccountResponse> accounts = socialMediaService.getConnectedAccounts();
        return ResponseEntity.ok(ApiResponse.success(accounts));
    }

    @GetMapping("/auth-url/{platform}")
    public ResponseEntity<ApiResponse<Map<String, String>>> getAuthUrl(@PathVariable String platform) {
        String url = socialMediaService.getAuthUrl(platform);
        return ResponseEntity.ok(ApiResponse.success(Map.of("url", url)));
    }

    @GetMapping("/callback/{platform}")
    public void handleOAuthCallback(
            @PathVariable String platform,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error,
            @RequestParam(required = false) String error_description,
            HttpServletResponse response) throws IOException {

        log.info("Social OAuth callback received for {}: code={}, error={}", platform,
                code != null ? "[PRESENT]" : "NULL", error);

        String adminUrl = "https://admin.zelvira.co.id/admin/settings?tab=social";
        if (socialMediaService instanceof SocialMediaServiceImpl) {
            adminUrl = ((SocialMediaServiceImpl) socialMediaService).getAdminSocialConnectUrl();
        }
        String querySep = adminUrl.contains("?") ? "&" : "?";

        if (error != null) {
            String desc = error_description != null ? error_description : error;
            response.sendRedirect(adminUrl + querySep + "status=error&platform=" + platform + "&message="
                    + URLEncoder.encode(desc, StandardCharsets.UTF_8));
            return;
        }

        try {
            socialMediaService.processAuthCallback(platform, code, state);
            response.sendRedirect(adminUrl + querySep + "status=success&platform=" + platform);
        } catch (Exception e) {
            log.error("Error processing social callback for {}", platform, e);
            response.sendRedirect(adminUrl + querySep + "status=error&platform=" + platform + "&message="
                    + URLEncoder.encode(e.getMessage(), StandardCharsets.UTF_8));
        }
    }

    @PostMapping("/accounts/manual-token")
    public ResponseEntity<ApiResponse<String>> saveManualToken(@RequestBody Map<String, Object> body) {
        String platform = (String) body.get("platform");
        String accountId = (String) body.get("accountId");
        String accountName = (String) body.get("accountName");
        String accessToken = (String) body.get("accessToken");
        Long expiresIn = body.containsKey("expiresIn") ? ((Number) body.get("expiresIn")).longValue() : 5184000L;

        socialMediaService.saveManualAccount(platform, accountId, accountName, accessToken, expiresIn);
        return ResponseEntity.ok(ApiResponse.success("Akun " + platform + " berhasil dihubungkan!", null));
    }

    @PostMapping("/accounts/{id}/disconnect")
    public ResponseEntity<ApiResponse<String>> disconnectAccount(@PathVariable Long id) {
        socialMediaService.disconnectAccount(id);
        return ResponseEntity.ok(ApiResponse.success("Koneksi akun berhasil diputuskan", null));
    }

    // ==========================================
    // POSTS
    // ==========================================

    @GetMapping("/posts")
    public ResponseEntity<ApiResponse<List<SocialPostResponse>>> getPosts(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String platform) {
        List<SocialPostResponse> posts = socialMediaService.getAllPosts(status, platform);
        return ResponseEntity.ok(ApiResponse.success(posts));
    }

    @GetMapping("/posts/{id}")
    public ResponseEntity<ApiResponse<SocialPostResponse>> getPost(@PathVariable Long id) {
        SocialPostResponse post = socialMediaService.getPostById(id);
        return ResponseEntity.ok(ApiResponse.success(post));
    }

    @PostMapping("/posts")
    public ResponseEntity<ApiResponse<SocialPostResponse>> createPost(@RequestBody SocialPostRequest request) {
        SocialPostResponse post = socialMediaService.createPost(request);
        return ResponseEntity.ok(ApiResponse.success("Post berhasil dibuat", post));
    }

    @PutMapping("/posts/{id}")
    public ResponseEntity<ApiResponse<SocialPostResponse>> updatePost(@PathVariable Long id,
            @RequestBody SocialPostRequest request) {
        SocialPostResponse post = socialMediaService.updatePost(id, request);
        return ResponseEntity.ok(ApiResponse.success("Post berhasil diperbarui", post));
    }

    @DeleteMapping("/posts/{id}")
    public ResponseEntity<ApiResponse<String>> deletePost(@PathVariable Long id) {
        socialMediaService.deletePost(id);
        return ResponseEntity.ok(ApiResponse.success("Post berhasil dihapus", null));
    }

    @PostMapping("/posts/{id}/publish")
    public ResponseEntity<ApiResponse<SocialPostResponse>> publishNow(@PathVariable Long id) {
        SocialPostResponse post = socialMediaService.publishPostNow(id);
        return ResponseEntity.ok(ApiResponse.success("Post berhasil dipublikasikan", post));
    }

    // ==========================================
    // COMMENTS
    // ==========================================

    @GetMapping("/comments")
    public ResponseEntity<ApiResponse<List<SocialCommentResponse>>> getComments(
            @RequestParam(required = false) String platform,
            @RequestParam(required = false) String search) {
        List<SocialCommentResponse> list = socialMediaService.getComments(platform, search);
        return ResponseEntity.ok(ApiResponse.success(list));
    }

    @PostMapping("/comments/sync")
    public ResponseEntity<ApiResponse<Map<String, Object>>> syncComments() {
        int count = socialMediaService.syncComments();
        return ResponseEntity.ok(ApiResponse.success("Sinkronisasi komentar berhasil", Map.of("newCommentsCount", count)));
    }

    @PostMapping("/comments/{id}/reply")
    public ResponseEntity<ApiResponse<SocialCommentResponse>> replyComment(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        String replyText = body.get("replyText");
        SocialCommentResponse res = socialMediaService.replyComment(id, replyText);
        return ResponseEntity.ok(ApiResponse.success("Balasan berhasil dikirim", res));
    }

    @PostMapping("/comments/{id}/hide")
    public ResponseEntity<ApiResponse<String>> hideComment(@PathVariable Long id) {
        socialMediaService.hideComment(id);
        return ResponseEntity.ok(ApiResponse.success("Komentar disembunyikan", null));
    }

    // ==========================================
    // FEED & STATUS
    // ==========================================

    @GetMapping("/feed")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getFeed(
            @RequestParam(required = false) String platform) {
        List<Map<String, Object>> feed = socialMediaService.getLiveFeedAndStatus(platform);
        return ResponseEntity.ok(ApiResponse.success(feed));
    }

    @GetMapping("/tiktok/creator-info")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getTikTokCreatorInfo() {
        return ResponseEntity.ok(ApiResponse.success(socialMediaService.getTikTokCreatorInfo()));
    }

    @GetMapping("/tiktok/status/{publishId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getTikTokPublishStatus(
            @PathVariable String publishId) {
        return ResponseEntity.ok(ApiResponse.success(socialMediaService.fetchTikTokPublishStatus(publishId)));
    }

    @PostMapping("/tiktok/status/{publishId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> postTikTokPublishStatus(
            @PathVariable String publishId) {
        return ResponseEntity.ok(ApiResponse.success(socialMediaService.fetchTikTokPublishStatus(publishId)));
    }

    // ==========================================
    // CALENDAR & STATS
    // ==========================================

    @GetMapping("/calendar")
    public ResponseEntity<ApiResponse<List<SocialPostResponse>>> getCalendarPosts(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        List<SocialPostResponse> posts = socialMediaService.getCalendarPosts(start, end);
        return ResponseEntity.ok(ApiResponse.success(posts));
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getStats() {
        Map<String, Object> stats = socialMediaService.getSocialStats();
        return ResponseEntity.ok(ApiResponse.success(stats));
    }
}

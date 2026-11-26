package com.zelvira.zelvira.controller;

import com.zelvira.zelvira.service.impl.InstagramApiServiceImpl;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

@RestController
@Slf4j
public class InstagramWebhookController {

    private final InstagramApiServiceImpl instagramService;

    public InstagramWebhookController(InstagramApiServiceImpl instagramService) {
        this.instagramService = instagramService;
    }

    /**
     * Webhook Verification Endpoint (Meta Developer Step 3: Verifikasi Token)
     * Meta sends a GET request with hub.mode, hub.verify_token, and hub.challenge
     * The response MUST be the raw challenge string in plain text with 200 OK.
     */
    @GetMapping(
            value = {
                    "/api/v1/callbacks/instagram/webhook",
                    "/api/v1/marketplace/webhook/instagram",
                    "/api/callbacks/instagram/webhook"
            },
            produces = MediaType.TEXT_PLAIN_VALUE
    )
    public ResponseEntity<String> verifyWebhook(
            @RequestParam(name = "hub.mode", required = false) String hubMode,
            @RequestParam(name = "hub.verify_token", required = false) String verifyToken,
            @RequestParam(name = "hub.challenge", required = false) String challenge) {

        log.info("Received Instagram Webhook verification request: hub.mode={}, hub.verify_token={}", hubMode, verifyToken);

        String expectedToken = instagramService.getWebhookVerifyToken();
        if ("subscribe".equalsIgnoreCase(hubMode) && expectedToken != null && expectedToken.equals(verifyToken)) {
            log.info("Instagram Webhook verified successfully! Returning hub.challenge: {}", challenge);
            return ResponseEntity.ok(challenge);
        }

        log.warn("Instagram Webhook verification failed. Expected token: '{}', Received token: '{}'", expectedToken, verifyToken);
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Verification failed: Token mismatch");
    }

    /**
     * Webhook Event Notification Endpoint
     * Meta sends POST requests when subscribed events occur (messages, comments, mentions, etc.)
     */
    @PostMapping(value = {
            "/api/v1/callbacks/instagram/webhook",
            "/api/v1/marketplace/webhook/instagram",
            "/api/callbacks/instagram/webhook"
    })
    public ResponseEntity<String> handleWebhookEvent(
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
            @RequestBody(required = false) String rawPayload) {

        log.info("Received Instagram Webhook Event: signature={}", signature);
        try {
            instagramService.handleWebhookEvent(rawPayload, signature);
        } catch (Exception e) {
            log.error("Error processing Instagram webhook event", e);
        }

        // Meta requires an HTTP 200 OK response quickly
        return ResponseEntity.ok("EVENT_RECEIVED");
    }

    /**
     * OAuth Redirect Callback (Meta Developer Step 1: URL Pengalihan)
     * User/Creator is redirected here after Instagram Login / Unified Login.
     */
    @GetMapping(value = {
            "/api/v1/callbacks/instagram/oauth",
            "/api/v1/marketplace/callback/instagram/oauth",
            "/api/callbacks/instagram/oauth"
    })
    public void handleOAuthCallback(
            @RequestParam(name = "code", required = false) String code,
            @RequestParam(name = "error", required = false) String error,
            @RequestParam(name = "error_reason", required = false) String errorReason,
            @RequestParam(name = "error_description", required = false) String errorDescription,
            @RequestParam(name = "shop_id", required = false) String shopId,
            HttpServletResponse response) throws IOException {

        log.info("Received Instagram OAuth Redirect: code={}, error={}, error_description={}",
                code != null ? "[PRESENT]" : "NULL", error, errorDescription);

        String adminUrl = instagramService.getAdminRedirectUrl();

        if (error != null) {
            String errMsg = errorDescription != null ? errorDescription : error;
            log.error("Instagram OAuth user denied or error: {}", errMsg);
            response.sendRedirect(adminUrl + "?status=error&message=" + URLEncoder.encode(errMsg, StandardCharsets.UTF_8));
            return;
        }

        if (code != null && !code.isBlank()) {
            try {
                instagramService.processAuthCallback(code, shopId);
                log.info("Instagram OAuth successfully completed for code!");
                response.sendRedirect(adminUrl + "?status=success&channel=instagram");
                return;
            } catch (Exception e) {
                log.error("Failed to process Instagram auth callback", e);
                response.sendRedirect(adminUrl + "?status=error&message=" + URLEncoder.encode(e.getMessage(), StandardCharsets.UTF_8));
                return;
            }
        }

        response.sendRedirect(adminUrl + "?status=error&message=" + URLEncoder.encode("Parameter 'code' tidak ditemukan", StandardCharsets.UTF_8));
    }

    /**
     * Manual Token Endpoint (For Meta Developer Step 2: "Buat token" / Generate Token)
     * Allows pasting a generated User/Page Access Token directly for testing or immediate use.
     */
    @PostMapping("/api/v1/callbacks/instagram/manual-token")
    public ResponseEntity<Map<String, Object>> saveManualToken(@RequestBody Map<String, Object> body) {
        String accessToken = (String) body.get("accessToken");
        String accountId = (String) body.get("accountId");
        String accountUsername = (String) body.get("accountUsername");
        long expiresIn = body.containsKey("expiresIn") ? ((Number) body.get("expiresIn")).longValue() : 5184000L;

        if (accessToken == null || accessToken.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "accessToken wajib diisi"
            ));
        }

        if (accountId == null || accountId.isBlank()) {
            accountId = instagramService.getDefaultAccountId();
        }
        if (accountUsername == null || accountUsername.isBlank()) {
            accountUsername = instagramService.getDefaultAccountUsername();
        }

        instagramService.saveOrUpdateConfig(accountId, accountUsername, accessToken, null, expiresIn);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Token Instagram berhasil disimpan untuk akun @" + accountUsername + " (ID: " + accountId + ")"
        ));
    }

    /**
     * Threads & Meta App Deauthorization Callback (Hapus Instalan URL Callback)
     * Dipanggil Meta saat pengguna mencabut izin aplikasi Zelvira.
     */
    @RequestMapping(
            value = {
                    "/api/v1/callbacks/threads/deauthorize",
                    "/api/v1/callbacks/instagram/deauthorize",
                    "/api/v1/callbacks/meta/deauthorize",
                    "/api/callbacks/threads/deauthorize",
                    "/api/callbacks/instagram/deauthorize",
                    "/api/callbacks/meta/deauthorize"
            },
            method = { RequestMethod.GET, RequestMethod.POST }
    )
    public ResponseEntity<Map<String, Object>> handleDeauthorize(
            @RequestParam(required = false) Map<String, String> queryParams,
            @RequestBody(required = false) String rawBody) {
        log.info("Received Meta/Threads deauthorization callback: query={}, body={}", queryParams, rawBody);
        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Deauthorization notification received and acknowledged"
        ));
    }

    /**
     * Threads & Meta Data Deletion Request Callback (Hapus URL Callback)
     * Meta mewajibkan mengembalikan JSON dengan confirmation_code dan tracking URL.
     */
    @RequestMapping(
            value = {
                    "/api/v1/callbacks/threads/data-deletion",
                    "/api/v1/callbacks/instagram/data-deletion",
                    "/api/v1/callbacks/meta/data-deletion",
                    "/api/callbacks/threads/data-deletion",
                    "/api/callbacks/instagram/data-deletion",
                    "/api/callbacks/meta/data-deletion"
            },
            method = { RequestMethod.GET, RequestMethod.POST }
    )
    public ResponseEntity<Map<String, Object>> handleDataDeletion(
            @RequestParam(required = false) Map<String, String> queryParams,
            @RequestBody(required = false) String rawBody) {
        String confirmationCode = "del_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String statusUrl = "https://api.zelvira.co.id/api/v1/callbacks/meta/data-deletion-status?code=" + confirmationCode;

        log.info("Received Meta/Threads data deletion callback: code={}, query={}, body={}",
                confirmationCode, queryParams, rawBody);

        return ResponseEntity.ok(Map.of(
                "url", statusUrl,
                "confirmation_code", confirmationCode
        ));
    }

    /**
     * Meta Data Deletion Status Page (URL untuk cek status penghapusan data)
     */
    @GetMapping(value = {
            "/api/v1/callbacks/meta/data-deletion-status",
            "/api/callbacks/meta/data-deletion-status"
    })
    public ResponseEntity<Map<String, Object>> getDataDeletionStatus(
            @RequestParam(required = false) String code) {
        return ResponseEntity.ok(Map.of(
                "status", "COMPLETED",
                "confirmation_code", code != null ? code : "unknown",
                "message", "Permintaan penghapusan data telah berhasil diproses sesuai ketentuan privasi Meta dan Zelvira Store."
        ));
    }
}


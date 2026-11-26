package com.zelvira.zelvira.security.ai;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;

@Component
public class AiKeyGenerator {

    private static final String ALPHANUM = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    public String generateRawToken(String agentId) {
        String roleSlug = agentId != null
                ? agentId.toLowerCase().replace("hermes-", "").replaceAll("[^a-z0-9]", "")
                : "agent";
        if (roleSlug.isBlank()) {
            roleSlug = "agent";
        }
        StringBuilder sb = new StringBuilder("zl_ai_").append(roleSlug).append("_");
        for (int i = 0; i < 32; i++) {
            sb.append(ALPHANUM.charAt(SECURE_RANDOM.nextInt(ALPHANUM.length())));
        }
        return sb.toString();
    }

    public String extractPrefix(String rawToken) {
        if (rawToken == null) return "";
        int len = Math.min(rawToken.length(), 18);
        return rawToken.substring(0, len) + "...";
    }

    public String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encoded = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(encoded);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    public boolean constantTimeEquals(String hashA, String hashB) {
        if (hashA == null || hashB == null) return false;
        return MessageDigest.isEqual(
                hashA.getBytes(StandardCharsets.UTF_8),
                hashB.getBytes(StandardCharsets.UTF_8));
    }
}

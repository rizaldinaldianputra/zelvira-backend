package com.zelvira.zelvira.security.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zelvira.zelvira.dto.ai.AiApiResponse;
import com.zelvira.zelvira.entity.AiAgent;
import com.zelvira.zelvira.entity.AiApiKey;
import com.zelvira.zelvira.entity.AiAuditLog;
import com.zelvira.zelvira.repository.AiAgentRepository;
import com.zelvira.zelvira.repository.AiApiKeyRepository;
import com.zelvira.zelvira.repository.AiAuditLogRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class HermesAiAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(HermesAiAuthFilter.class);
    public static final String ATTR_AI_PROFILE = "HERMES_AI_PROFILE";

    private final ObjectMapper objectMapper;
    private final AiApiKeyRepository apiKeyRepository;
    private final AiAgentRepository agentRepository;
    private final AiAuditLogRepository auditLogRepository;
    private final AiKeyGenerator keyGenerator;

    @Value("${app.ai.hermes.ceo-key:${HERMES_CEO_API_KEY:}}")
    private String ceoKey;

    @Value("${app.ai.hermes.content-key:${HERMES_CONTENT_API_KEY:}}")
    private String contentKey;

    @Value("${app.ai.hermes.marketing-key:${HERMES_MARKETING_API_KEY:}}")
    private String marketingKey;

    @Value("${app.ai.hermes.seo-key:${HERMES_SEO_API_KEY:}}")
    private String seoKey;

    @Value("${app.ai.hermes.admin-key:${HERMES_ADMIN_API_KEY:}}")
    private String adminKey;

    @Value("${app.ai.hermes.shared-key:${HERMES_AI_API_KEY:}}")
    private String sharedKey;

    @Value("${app.ai.hermes.rate-limit-per-minute:120}")
    private int rateLimitPerMinute;

    private final Map<String, Deque<Long>> rateLimitBuckets = new ConcurrentHashMap<>();

    public HermesAiAuthFilter(
            ObjectMapper objectMapper,
            AiApiKeyRepository apiKeyRepository,
            AiAgentRepository agentRepository,
            AiAuditLogRepository auditLogRepository,
            AiKeyGenerator keyGenerator) {
        this.objectMapper = objectMapper;
        this.apiKeyRepository = apiKeyRepository;
        this.agentRepository = agentRepository;
        this.auditLogRepository = auditLogRepository;
        this.keyGenerator = keyGenerator;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path == null || !path.startsWith("/api/v1/ai/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        if (!"GET".equalsIgnoreCase(request.getMethod())) {
            writeError(response, HttpServletResponse.SC_METHOD_NOT_ALLOWED,
                    "AI Integration API is strictly READ-ONLY. Only GET requests are permitted.", null);
            return;
        }

        String apiKey = extractApiKey(request);
        String profileHeader = request.getHeader("X-AI-Profile");

        if (apiKey == null || apiKey.isBlank()) {
            log.warn("[HERMES-AI-AUDIT] UNAUTHORIZED missing API key | ip={} | path={}",
                    request.getRemoteAddr(), request.getRequestURI());
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "Missing X-AI-API-Key header", profileHeader);
            return;
        }

        AiProfile resolvedProfile = resolveProfile(apiKey, profileHeader);
        if (resolvedProfile == null) {
            log.warn("[HERMES-AI-AUDIT] FORBIDDEN invalid API key or profile | profileHeader={} | ip={} | path={}",
                    profileHeader, request.getRemoteAddr(), request.getRequestURI());
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "Invalid AI API key or profile", profileHeader);
            return;
        }

        if (!allowRequest(resolvedProfile.getId())) {
            log.warn("[HERMES-AI-AUDIT] RATE_LIMITED | profile={} | ip={} | path={}",
                    resolvedProfile.getId(), request.getRemoteAddr(), request.getRequestURI());
            writeError(response, 429, "Rate limit exceeded for profile " + resolvedProfile.getId(),
                    resolvedProfile.getId());
            return;
        }

        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_HERMES_AI"));
        for (AiPermission perm : resolvedProfile.getPermissions()) {
            authorities.add(new SimpleGrantedAuthority("AI_PERM_" + perm.name()));
        }

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                resolvedProfile.getId(), null, authorities);
        SecurityContextHolder.getContext().setAuthentication(auth);
        request.setAttribute(ATTR_AI_PROFILE, resolvedProfile);

        long start = System.currentTimeMillis();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMs = System.currentTimeMillis() - start;
            log.info("[HERMES-AI-AUDIT] profile={} | method={} | path={} | query={} | status={} | durationMs={} | ip={}",
                    resolvedProfile.getId(), request.getMethod(), request.getRequestURI(),
                    request.getQueryString(), response.getStatus(), durationMs, request.getRemoteAddr());
            try {
                auditLogRepository.save(AiAuditLog.builder()
                        .agentId(resolvedProfile.getId())
                        .endpoint(request.getRequestURI())
                        .httpMethod(request.getMethod())
                        .responseStatus(response.getStatus())
                        .ipAddress(request.getRemoteAddr())
                        .durationMs(durationMs)
                        .build());
            } catch (Exception ignored) {
            }
        }
    }

    private String extractApiKey(HttpServletRequest request) {
        String key = request.getHeader("X-AI-API-Key");
        if (key != null && !key.isBlank()) {
            return key.trim();
        }
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            return auth.substring(7).trim();
        }
        return null;
    }

    private AiProfile resolveProfile(String apiKey, String profileHeader) {
        try {
            String hash = keyGenerator.hashToken(apiKey);
            var dbKeyOpt = apiKeyRepository.findByTokenHashWithAgent(hash);
            if (dbKeyOpt.isPresent()) {
                AiApiKey dbKey = dbKeyOpt.get();
                AiAgent agent = dbKey.getAgent();
                if (!Boolean.TRUE.equals(dbKey.getIsActive()) || dbKey.getRevokedAt() != null
                        || agent == null || !Boolean.TRUE.equals(agent.getIsActive())) {
                    return null;
                }
                if (profileHeader != null && !profileHeader.isBlank()
                        && !agent.getAgentId().equalsIgnoreCase(profileHeader.trim())) {
                    return null; // Prevent one agent from impersonating another
                }
                java.time.LocalDateTime now = java.time.LocalDateTime.now();
                dbKey.setLastUsedAt(now);
                agent.setLastUsedAt(now);
                apiKeyRepository.save(dbKey);
                agentRepository.save(agent);
                return AiProfile.fromId(agent.getAgentId());
            }
        } catch (Exception ignored) {
        }

        if (constantTimeEquals(apiKey, ceoKey)) return AiProfile.HERMES_CEO;
        if (constantTimeEquals(apiKey, contentKey)) return AiProfile.HERMES_CONTENT;
        if (constantTimeEquals(apiKey, marketingKey)) return AiProfile.HERMES_MARKETING;
        if (constantTimeEquals(apiKey, seoKey)) return AiProfile.HERMES_SEO;
        if (constantTimeEquals(apiKey, adminKey)) return AiProfile.HERMES_ADMIN;

        if (constantTimeEquals(apiKey, sharedKey)) {
            return AiProfile.fromId(profileHeader);
        }
        return null;
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || b.isBlank()) return false;
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    private boolean allowRequest(String profileId) {
        long now = System.currentTimeMillis();
        long windowStart = now - 60_000L;
        Deque<Long> timestamps = rateLimitBuckets.computeIfAbsent(profileId, k -> new ArrayDeque<>());
        synchronized (timestamps) {
            while (!timestamps.isEmpty() && timestamps.peekFirst() < windowStart) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= rateLimitPerMinute) {
                return false;
            }
            timestamps.addLast(now);
            return true;
        }
    }

    private void writeError(HttpServletResponse response, int status, String message, String profile)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), AiApiResponse.error(message, profile));
    }
}

package com.zelvira.zelvira.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SocialPostRequest {
    private Long productId;
    private String title;
    private String caption;
    private String mediaType; // VIDEO, IMAGE, CAROUSEL, TEXT
    private String mediaUrls;
    private String thumbnailUrl;
    private String targetPlatforms; // TIKTOK, INSTAGRAM, ALL
    private String postType; // FEED, STORY, REEL
    private String status; // DRAFT, SCHEDULED, PUBLISHED
    private LocalDateTime scheduledAt;
    private String privacyLevel; // PUBLIC_TO_EVERYONE, SELF_ONLY
    private String tiktokPostMode; // DIRECT_POST, MEDIA_UPLOAD
    private Boolean publishNow;
}

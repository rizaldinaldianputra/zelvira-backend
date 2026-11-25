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
public class SocialPostResponse {
    private Long id;
    private Long productId;
    private String title;
    private String caption;
    private String mediaType;
    private String mediaUrls;
    private String thumbnailUrl;
    private String targetPlatforms;
    private String postType;
    private String status;
    private LocalDateTime scheduledAt;
    private LocalDateTime publishedAt;
    private String errorMessage;
    private String tiktokPublishId;
    private String instagramMediaId;
    private String linkedinPostUrn;
    private String youtubeVideoId;
    private String privacyLevel;
    private String tiktokPostMode;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

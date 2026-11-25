package com.zelvira.zelvira.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "social_posts")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SocialPost {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id")
    private Long productId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", insertable = false, updatable = false)
    @JsonIgnore
    private Product product;

    @Column(name = "created_by_user_id")
    private Long createdByUserId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id", insertable = false, updatable = false)
    @JsonIgnore
    private User createdByUser;

    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    @Builder.Default
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<SocialComment> comments = new ArrayList<>();

    @Column(name = "title", length = 255)
    private String title;

    @Column(name = "caption", columnDefinition = "TEXT")
    private String caption;

    @Column(name = "media_type", length = 50)
    @Builder.Default
    private String mediaType = "VIDEO"; // VIDEO, IMAGE, CAROUSEL, TEXT

    @Column(name = "media_urls", columnDefinition = "TEXT")
    private String mediaUrls; // JSON or comma-separated URLs

    @Column(name = "thumbnail_url", columnDefinition = "TEXT")
    private String thumbnailUrl;

    @Column(name = "target_platforms", length = 100)
    @Builder.Default
    private String targetPlatforms = "TIKTOK"; // TIKTOK, INSTAGRAM, ALL

    @Column(name = "post_type", length = 50)
    @Builder.Default
    private String postType = "FEED"; // FEED, STORY, REEL

    @Column(name = "status", length = 50, nullable = false)
    @Builder.Default
    private String status = "DRAFT"; // DRAFT, SCHEDULED, PUBLISHING, PUBLISHED, FAILED

    @Column(name = "scheduled_at")
    private LocalDateTime scheduledAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "tiktok_publish_id", length = 255)
    private String tiktokPublishId;

    @Column(name = "instagram_media_id", length = 255)
    private String instagramMediaId;

    @Column(name = "linkedin_post_urn", length = 255)
    private String linkedinPostUrn;

    @Column(name = "youtube_video_id", length = 255)
    private String youtubeVideoId;

    @Column(name = "threads_media_id", length = 255)
    private String threadsMediaId;

    @Column(name = "facebook_post_id", length = 255)
    private String facebookPostId;

    @Column(name = "privacy_level", length = 50)
    @Builder.Default
    private String privacyLevel = "PUBLIC_TO_EVERYONE"; // PUBLIC_TO_EVERYONE, MUTUAL_FOLLOW_FRIENDS, SELF_ONLY

    @Column(name = "tiktok_post_mode", length = 50)
    @Builder.Default
    private String tiktokPostMode = "DIRECT_POST"; // DIRECT_POST, MEDIA_UPLOAD

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public String getExternalPostId() {
        if (tiktokPublishId != null && !tiktokPublishId.isBlank()) return tiktokPublishId;
        if (instagramMediaId != null && !instagramMediaId.isBlank()) return instagramMediaId;
        if (linkedinPostUrn != null && !linkedinPostUrn.isBlank()) return linkedinPostUrn;
        if (youtubeVideoId != null && !youtubeVideoId.isBlank()) return youtubeVideoId;
        if (threadsMediaId != null && !threadsMediaId.isBlank()) return threadsMediaId;
        if (facebookPostId != null && !facebookPostId.isBlank()) return facebookPostId;
        return null;
    }
}

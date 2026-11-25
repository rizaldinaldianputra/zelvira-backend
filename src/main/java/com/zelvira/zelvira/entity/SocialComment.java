package com.zelvira.zelvira.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "social_comments")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SocialComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "post_id")
    private Long postId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", insertable = false, updatable = false)
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private SocialPost post;

    @Column(name = "social_account_id")
    private Long socialAccountId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "social_account_id", insertable = false, updatable = false)
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private SocialAccount socialAccount;

    @Column(name = "platform", nullable = false, length = 50)
    private String platform; // TIKTOK, INSTAGRAM, LINKEDIN, X, FACEBOOK, YOUTUBE

    @Column(name = "author_name", length = 255)
    private String authorName;

    @Column(name = "author_username", length = 255)
    private String authorUsername;

    @Column(name = "author_avatar_url", columnDefinition = "TEXT")
    private String authorAvatarUrl;

    @Column(name = "comment_text", columnDefinition = "TEXT", nullable = false)
    private String commentText;

    @Column(name = "post_title", length = 255)
    private String postTitle;

    @Column(name = "post_thumbnail_url", columnDefinition = "TEXT")
    private String postThumbnailUrl;

    @Column(name = "external_comment_id", length = 255)
    private String externalCommentId;

    @Column(name = "external_post_id", length = 255)
    private String externalPostId;

    @Column(name = "reply_text", columnDefinition = "TEXT")
    private String replyText;

    @Column(name = "is_replied", nullable = false)
    @Builder.Default
    private Boolean isReplied = false;

    @Column(name = "replied_at")
    private LocalDateTime repliedAt;

    @Column(name = "status", length = 50)
    @Builder.Default
    private String status = "PENDING"; // PENDING, REPLIED, HIDDEN

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}

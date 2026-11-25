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
public class SocialCommentResponse {
    private Long id;
    private Long postId;
    private Long socialAccountId;
    private String accountName;
    private String platform;
    private String authorName;
    private String authorUsername;
    private String authorAvatarUrl;
    private String commentText;
    private String postTitle;
    private String postThumbnailUrl;
    private String externalCommentId;
    private String externalPostId;
    private String replyText;
    private Boolean isReplied;
    private LocalDateTime repliedAt;
    private String status;
    private LocalDateTime createdAt;
}


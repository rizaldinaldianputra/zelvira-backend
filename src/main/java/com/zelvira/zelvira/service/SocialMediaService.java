package com.zelvira.zelvira.service;

import com.zelvira.zelvira.dto.SocialAccountResponse;
import com.zelvira.zelvira.dto.SocialCommentResponse;
import com.zelvira.zelvira.dto.SocialPostRequest;
import com.zelvira.zelvira.dto.SocialPostResponse;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface SocialMediaService {
    // Accounts
    List<SocialAccountResponse> getConnectedAccounts();
    String getAuthUrl(String platform);
    void processAuthCallback(String platform, String code, String state);
    void saveManualAccount(String platform, String accountId, String accountName, String accessToken, Long expiresInSeconds);
    void disconnectAccount(Long id);

    // Posts & Composer
    List<SocialPostResponse> getAllPosts(String status, String platform);
    SocialPostResponse getPostById(Long id);
    SocialPostResponse createPost(SocialPostRequest request);
    SocialPostResponse updatePost(Long id, SocialPostRequest request);
    void deletePost(Long id);
    SocialPostResponse publishPostNow(Long id);

    // Comments & Engagement
    List<SocialCommentResponse> getComments(String platform, String search);
    SocialCommentResponse replyComment(Long commentId, String replyText);
    void hideComment(Long commentId);
    int syncComments();

    // Feed & Status
    List<Map<String, Object>> getLiveFeedAndStatus(String platform);

    // TikTok Content Posting Helpers
    Map<String, Object> getTikTokCreatorInfo();
    Map<String, Object> fetchTikTokPublishStatus(String publishId);

    // Calendar & Analytics
    List<SocialPostResponse> getCalendarPosts(LocalDateTime start, LocalDateTime end);
    Map<String, Object> getSocialStats();

    // Background Scheduler
    void processDueScheduledPosts();
}

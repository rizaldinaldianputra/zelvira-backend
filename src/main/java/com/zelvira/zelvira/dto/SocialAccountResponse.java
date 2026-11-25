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
public class SocialAccountResponse {
    private Long id;
    private String platform;
    private String accountName;
    private String accountId;
    private String avatarUrl;
    private Boolean isActive;
    private Boolean hasToken;
    private LocalDateTime tokenExpiry;
    private String clientKey;
    private LocalDateTime createdAt;
}


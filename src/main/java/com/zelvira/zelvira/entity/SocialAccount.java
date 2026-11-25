package com.zelvira.zelvira.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "social_accounts")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SocialAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    @JsonIgnore
    private User user;

    @Column(name = "platform", nullable = false, length = 50)
    private String platform; // TIKTOK, INSTAGRAM, FACEBOOK, YOUTUBE, LINKEDIN, X

    @Column(name = "account_name", length = 255)
    private String accountName; // e.g. @zelviraofficialstore

    @Column(name = "account_id", length = 100)
    private String accountId; // e.g. open_id or ig_user_id

    @Column(name = "avatar_url", columnDefinition = "TEXT")
    private String avatarUrl;

    @JsonIgnore
    @Column(name = "access_token", columnDefinition = "TEXT")
    private String accessToken;

    @JsonIgnore
    @Column(name = "refresh_token", columnDefinition = "TEXT")
    private String refreshToken;

    @Column(name = "token_expiry")
    private LocalDateTime tokenExpiry;

    @Column(name = "client_key", length = 255)
    private String clientKey;

    @JsonIgnore
    @Column(name = "client_secret", length = 255)
    private String clientSecret;

    @Column(name = "scopes", columnDefinition = "TEXT")
    private String scopes;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}

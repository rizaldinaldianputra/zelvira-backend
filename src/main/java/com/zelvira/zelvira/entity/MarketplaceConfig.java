package com.zelvira.zelvira.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Id;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Column;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

@Entity
@Table(name = "marketplace_config")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MarketplaceConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "marketplace_type", nullable = false, length = 50)
    private String marketplaceType; // SHOPEE, TOKOPEDIA, TIKTOK

    @Column(name = "shop_id", length = 100)
    private String shopId;

    @Column(name = "shop_name", length = 255)
    private String shopName;

    @JsonIgnore
    @Column(name = "shop_cipher", columnDefinition = "TEXT")
    private String shopCipher;

    @Column(name = "region", length = 50)
    private String region;

    @Column(name = "seller_name", length = 255)
    private String sellerName;

    @JsonIgnore
    @Column(name = "access_token", columnDefinition = "TEXT")
    private String accessToken;

    @JsonIgnore
    @Column(name = "refresh_token", columnDefinition = "TEXT")
    private String refreshToken;

    @Column(name = "token_expiry")
    private LocalDateTime tokenExpiry;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}

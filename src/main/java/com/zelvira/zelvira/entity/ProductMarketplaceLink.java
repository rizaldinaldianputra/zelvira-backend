package com.zelvira.zelvira.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Id;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Column;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.FetchType;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

@Entity
@Table(name = "product_marketplace_link")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductMarketplaceLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "marketplace_type", nullable = false, length = 50)
    private String marketplaceType; // SHOPEE, TOKOPEDIA, TIKTOK

    @Column(name = "marketplace_product_id", length = 100)
    private String marketplaceProductId;

    @Column(length = 50)
    private String status; // SYNCED, FAILED, PENDING

    @Column(name = "last_sync_time")
    private LocalDateTime lastSyncTime;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}

package com.zelvira.zelvira.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "flash_sale")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FlashSale {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "platform", length = 20, nullable = false)
    @Builder.Default
    private String platform = "WEB";

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @PrePersist
    @PreUpdate
    public void prePersist() {
        if (this.title == null)
            this.title = this.name;
        if (this.name == null)
            this.name = this.title;
    }

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;

    @Column(name = "banner_url", length = 500)
    private String bannerUrl;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "max_purchase_per_user")
    private Integer maxPurchasePerUser;

    @OneToMany(mappedBy = "flashSale", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<FlashSaleItem> items;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}

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
public class ContentItemRequest {
    private Long id;
    private String type;
    private String platform;
    private Boolean isActive;
    private String title;
    private String subtitle;
    private String imageUrl;
    private String linkUrl;
    private Long productId;
    private String tag;
    private String ctaText;
    private String badgeText;
    private String iconName;
    private String emoji;
    private String styleConfig;
    private Integer displayOrder;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private String contentBody;
    private String bannerUrl;
    private String productIds;
    private Long sectionId;
    private Double price;
    private Integer quantity;
    private String discountType;
    private Double discountValue;
    private Integer maxPurchase;
    private Integer soldQty;
}

package com.zelvira.zelvira.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductResponse {
    private Long id;
    private String name;
    private String slug;
    private String sku;
    private String description;
    private String shortDescription;
    private Long categoryId;
    private CategoryResponse category;
    private Long brandId;
    private BrandResponse brand;
    private String gender;
    private String material;
    private Integer weight;
    private Long pointsEarned;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Simplified fields for admin form
    private BigDecimal price;
    private Integer qty;
    private String thumbnailUrl; // Dedicated thumbnail for product cards
    private String thumbnail;    // Alias for thumbnailUrl
    private String imageUrl;     // Compatibility alias for thumbnail
    private List<ProductVariantResponse> variants;
    private List<String> banners; // Banners for product detail slider
    private List<String> images;  // Compatibility alias for banners / gallery

    private Double averageRating;
    private Integer reviewCount;
}

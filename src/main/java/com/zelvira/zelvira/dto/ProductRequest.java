package com.zelvira.zelvira.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class ProductRequest {
    @NotBlank(message = "Product name is required")
    private String name;

    private String slug;
    private String sku;

    private String description;
    private String shortDescription;
    private Long categoryId;
    private Long brandId;
    private String gender;
    private String material;
    private Integer weight;
    private Long pointsEarned;
    private Boolean isActive;

    // Simplified fields for admin form
    private BigDecimal price;
    private Integer qty;
    private String thumbnailUrl; // Dedicated thumbnail for product cards
    private String imageUrl;     // Compatibility alias for thumbnail
    private java.util.List<ProductVariantRequest> variants;
    private java.util.List<String> banners; // Banners for product detail slider
    private java.util.List<String> images;  // Compatibility alias for banners / gallery
}

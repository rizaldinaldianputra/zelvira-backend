package com.zelvira.zelvira.dto.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiProductResponse {
    private Long id;
    private String name;
    private String slug;
    private String sku;
    private String description;
    private String shortDescription;
    private CategorySummary category;
    private BrandSummary brand;
    private PriceSummary price;
    private List<VariantSummary> variants;
    private List<ImageSummary> images;
    private String status;
    private Integer weight;
    private String gender;
    private String material;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategorySummary {
        private Long id;
        private String name;
        private String slug;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BrandSummary {
        private Long id;
        private String name;
        private String slug;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PriceSummary {
        private BigDecimal normal;
        private BigDecimal selling;
        private BigDecimal promo;
        private String currency;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VariantSummary {
        private Long id;
        private String sku;
        private String name;
        private String size;
        private String color;
        private String hexColor;
        private BigDecimal price;
        private BigDecimal discountPrice;
        private Integer stock;
        private String status;
        private String imageUrl;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImageSummary {
        private String url;
        @JsonProperty("is_primary")
        private boolean primary;
        private Long variantId;
    }
}

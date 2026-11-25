package com.zelvira.zelvira.dto;

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
public class ProductPricingResponse {
    private Long id;
    private String name;
    private String sku;
    private String categoryName;
    private String imageUrl;
    private Integer totalStock;

    private BigDecimal currentMinPrice;
    private BigDecimal currentMaxPrice;
    private BigDecimal currentCostPrice;

    private BigDecimal calculatedHpp;
    private BigDecimal calculatedSellingPrice;
    private BigDecimal calculatedStrikePrice;
    private BigDecimal profitMarginPercent;
    private Boolean isApplied;

    private List<VariantPricingDto> variants;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VariantPricingDto {
        private Long id;
        private String sku;
        private String size;
        private String color;
        private Integer stock;
        private BigDecimal currentPrice;
        private BigDecimal currentDiscountPrice;
        private BigDecimal currentCostPrice;

        private BigDecimal calculatedHpp;
        private BigDecimal calculatedSellingPrice;
        private BigDecimal calculatedStrikePrice;
        private BigDecimal profitMarginPercent;
        private Boolean isApplied;
    }
}


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
public class PricingAnalyticsResponse {
    private BigDecimal totalInventoryHppValue;
    private BigDecimal totalRevenue;
    private BigDecimal totalEstimatedHppCost;
    private BigDecimal totalGrossProfit;
    private BigDecimal averageMarginPercent;
    private Long totalProductsCount;
    private Long profitableProductsCount;
    private Long lowMarginProductsCount;

    private List<ProductProfitabilityDto> products;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductProfitabilityDto {
        private Long productId;
        private String productName;
        private String sku;
        private String category;
        private String imageUrl;
        private Integer totalStock;

        private BigDecimal currentSellingPrice;
        private BigDecimal hppPerUnit;
        private BigDecimal unitProfit;
        private BigDecimal marginPercent;

        private Integer unitsSold;
        private BigDecimal revenue;
        private BigDecimal totalCost;
        private BigDecimal totalProfit;

        private String performanceLabel; // STAR, VOLUME_DRIVER, MODERATE, LOW_PERFORMER
    }
}


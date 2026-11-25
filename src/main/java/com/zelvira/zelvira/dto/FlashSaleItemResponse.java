package com.zelvira.zelvira.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FlashSaleItemResponse {
    private Long id;
    private Long productId;
    private String productName;
    private String productImageUrl;
    private Long variantId;
    private String variantName;
    private BigDecimal originalPrice;
    private BigDecimal discountPrice;
    private Integer stockLimit;
    private Integer flashStock;
    private Integer soldCount;
    private Integer maxPurchasePerUser;
}

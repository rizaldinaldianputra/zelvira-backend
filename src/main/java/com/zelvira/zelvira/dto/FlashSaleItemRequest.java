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
public class FlashSaleItemRequest {
    private Long id;
    private Long productId;
    private Long variantId;
    private BigDecimal discountPrice;
    private Integer stockLimit;
    private Integer maxPurchasePerUser;
}

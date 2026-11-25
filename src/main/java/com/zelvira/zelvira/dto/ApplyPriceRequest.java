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
public class ApplyPriceRequest {
    private Long productId;
    private Long variantId;
    private BigDecimal sellingPrice;
    private BigDecimal discountPrice;
    private BigDecimal costPrice;

    // Optional list of variant IDs to apply to all variants of this product
    private List<Long> variantIds;
}


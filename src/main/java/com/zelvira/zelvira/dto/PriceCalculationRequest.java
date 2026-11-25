package com.zelvira.zelvira.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PriceCalculationRequest {
    private Long productId;
    private Long variantId;
    private Long formulaId;
    private BigDecimal baseModal;
    private BigDecimal profitPercent;
    private BigDecimal profitNominal;
    private BigDecimal adminFeePercent;
    private BigDecimal bufferDiskonPercent;
    private String roundingRule;

    // Optional custom overrides for cost items, e.g. {"PLASTIK": 1200, "HANGTAG": 400}
    private Map<String, BigDecimal> costOverrides;
}


package com.zelvira.zelvira.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PricingFormulaResponse {
    private Long id;
    private String name;
    private String code;
    private String description;
    private String hppExpression;
    private String minPriceExpression;
    private String sellingPriceExpression;
    private String strikePriceExpression;
    private String roundingRule;
    private BigDecimal defaultProfitPercent;
    private BigDecimal defaultProfitNominal;
    private BigDecimal defaultAdminFeePercent;
    private BigDecimal defaultBufferDiskonPercent;
    private Boolean isDefault;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}


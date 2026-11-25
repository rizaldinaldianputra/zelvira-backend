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
public class PriceCalculationResponse {
    private BigDecimal baseModal;
    private BigDecimal packagingCost;
    private BigDecimal operationalCost;
    private BigDecimal marketingCost;
    private BigDecimal otherCost;
    private BigDecimal totalHpp;

    private BigDecimal minSellingPrice;
    private BigDecimal recommendedPrice;
    private BigDecimal roundedPrice;
    private BigDecimal strikePrice;

    private BigDecimal profitNominal;
    private BigDecimal marginPercent;
    private BigDecimal estimatedAdminFeeNominal;

    private String roundingRule;
    private Map<String, BigDecimal> variablesUsed;
    private Map<String, BigDecimal> itemizedCosts;
}


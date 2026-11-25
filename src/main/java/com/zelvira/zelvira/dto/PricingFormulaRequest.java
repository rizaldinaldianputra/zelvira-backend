package com.zelvira.zelvira.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PricingFormulaRequest {
    @NotBlank(message = "Nama formula wajib diisi")
    private String name;

    @NotBlank(message = "Kode formula wajib diisi")
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
}


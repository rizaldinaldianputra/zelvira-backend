package com.zelvira.zelvira.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PricingCostItemRequest {
    @NotBlank(message = "Kode biaya wajib diisi")
    private String code;

    @NotBlank(message = "Nama komponen biaya wajib diisi")
    private String name;

    private String category; // PACKAGING, OPERATIONAL, MARKETING, OTHER
    private String costType; // FIXED_AMOUNT, PERCENTAGE

    @NotNull(message = "Nominal/nilai default wajib diisi")
    private BigDecimal defaultAmount;

    private String unit;
    private String description;
    private Boolean isActive;
}


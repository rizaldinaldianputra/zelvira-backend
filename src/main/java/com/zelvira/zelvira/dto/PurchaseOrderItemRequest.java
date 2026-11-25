package com.zelvira.zelvira.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
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
public class PurchaseOrderItemRequest {

    @NotNull(message = "Product variant wajib dipilih")
    private Long variantId;

    @NotNull(message = "Jumlah kuantitas wajib diisi")
    @Min(value = 1, message = "Kuantitas minimal 1")
    private Integer quantity;

    @NotNull(message = "Harga beli satuan wajib diisi")
    @DecimalMin(value = "0.0", inclusive = true, message = "Harga satuan tidak boleh negatif")
    private BigDecimal unitCost;

    private String notes;
}

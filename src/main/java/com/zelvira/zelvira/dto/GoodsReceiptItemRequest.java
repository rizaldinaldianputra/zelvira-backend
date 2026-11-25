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
public class GoodsReceiptItemRequest {

    private Long purchaseOrderItemId;

    @NotNull(message = "Product variant wajib dipilih")
    private Long variantId;

    @NotNull(message = "Jumlah kuantitas diterima wajib diisi")
    @Min(value = 1, message = "Kuantitas diterima minimal 1")
    private Integer quantityReceived;

    @Builder.Default
    private Integer quantityRejected = 0;

    @DecimalMin(value = "0.0", inclusive = true, message = "Harga satuan tidak boleh negatif")
    private BigDecimal unitCost;

    private String notes;
}

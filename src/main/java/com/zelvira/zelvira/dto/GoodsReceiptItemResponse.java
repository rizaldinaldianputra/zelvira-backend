package com.zelvira.zelvira.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GoodsReceiptItemResponse {
    private Long id;
    private Long purchaseOrderItemId;
    private Long variantId;
    private String sku;
    private String productName;
    private String size;
    private String color;
    private Integer quantityReceived;
    private Integer quantityRejected;
    private BigDecimal unitCost;
    private String notes;
}

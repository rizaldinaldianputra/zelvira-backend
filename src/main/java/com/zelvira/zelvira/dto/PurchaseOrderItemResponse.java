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
public class PurchaseOrderItemResponse {
    private Long id;
    private Long variantId;
    private String sku;
    private String productName;
    private String size;
    private String color;
    private Integer currentStock;
    private Integer quantity;
    private Integer receivedQuantity;
    private Integer remainingQuantity;
    private BigDecimal unitCost;
    private BigDecimal subtotal;
    private String notes;
}

package com.zelvira.zelvira.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseOrderResponse {
    private Long id;
    private String poNumber;
    private Long supplierId;
    private String supplierCode;
    private String supplierName;
    private String supplierPhone;
    private LocalDate orderDate;
    private LocalDate expectedDeliveryDate;
    private String status;
    private String paymentStatus;
    private String paymentTerm;
    private BigDecimal subtotal;
    private BigDecimal taxAmount;
    private BigDecimal shippingCost;
    private BigDecimal discountAmount;
    private BigDecimal totalAmount;
    private String notes;
    private String createdBy;
    private String approvedBy;
    private LocalDateTime approvedAt;
    private Integer totalItems;
    private Integer totalOrderedQty;
    private Integer totalReceivedQty;
    private List<PurchaseOrderItemResponse> items;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

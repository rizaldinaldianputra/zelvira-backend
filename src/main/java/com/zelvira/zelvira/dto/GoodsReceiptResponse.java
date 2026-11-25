package com.zelvira.zelvira.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GoodsReceiptResponse {
    private Long id;
    private String receiptNumber;
    private Long purchaseOrderId;
    private String poNumber;
    private Long supplierId;
    private String supplierCode;
    private String supplierName;
    private LocalDate receiptDate;
    private String doNumber;
    private String receivedBy;
    private String approvedBy;
    private LocalDateTime approvedAt;
    private String status;
    private String notes;
    private Integer totalItems;
    private Integer totalReceivedQty;
    private Integer totalRejectedQty;
    private List<GoodsReceiptItemResponse> items;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

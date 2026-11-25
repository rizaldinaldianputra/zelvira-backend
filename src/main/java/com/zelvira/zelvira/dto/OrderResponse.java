package com.zelvira.zelvira.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderResponse {
    private Long id;
    private String orderNumber;
    private Long userId;
    private Double totalAmount;
    private String status;
    private String trackingNumber;
    private String paymentToken;
    private String paymentUrl;
    private Double shippingAmount;
    private String shippingService;
    private String destinationSubdistrictId;
    private String shippingAddress;
    private String shippingRecipient;
    private String shippingPhone;
    private String paymentMethod;
    private Double walletAmountUsed;
    private Long pointsUsed;
    private Double pointsDiscountAmount;
    private Long totalPointsEarned;
    private String voucherCode;
    private Double voucherDiscountAmount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<OrderItemResponse> items;
}

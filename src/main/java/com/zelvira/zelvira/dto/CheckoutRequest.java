package com.zelvira.zelvira.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CheckoutRequest {
    private String shippingService;
    private String destinationSubdistrictId;
    private String shippingAddress;
    private String shippingRecipient;
    private String shippingPhone;
    private Double shippingAmount;
    private String voucherCode;
    private Long productId;
    private Long variantId;
    private Integer quantity;
    private String paymentMethod; // MIDTRANS, WALLET
    private Double walletAmountUsed;
    private Long pointsUsed;
}

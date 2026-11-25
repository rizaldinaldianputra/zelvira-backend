package com.zelvira.zelvira.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseOrderRequest {

    private String poNumber;

    @NotNull(message = "Supplier wajib dipilih")
    private Long supplierId;

    @NotNull(message = "Tanggal pesanan wajib diisi")
    private LocalDate orderDate;

    private LocalDate expectedDeliveryDate;

    private String status; // DRAFT, ORDERED

    private String paymentStatus; // UNPAID, PARTIAL, PAID

    private String paymentTerm;

    private BigDecimal taxAmount;

    private BigDecimal shippingCost;

    private BigDecimal discountAmount;

    private String notes;

    @NotEmpty(message = "Pesanan harus memiliki minimal 1 item produk")
    @Valid
    private List<PurchaseOrderItemRequest> items;
}

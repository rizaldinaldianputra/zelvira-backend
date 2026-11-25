package com.zelvira.zelvira.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GoodsReceiptRequest {

    private String receiptNumber;

    private Long purchaseOrderId; // optional (linked to PO)

    @NotNull(message = "Supplier wajib dipilih")
    private Long supplierId;

    @NotNull(message = "Tanggal penerimaan wajib diisi")
    private LocalDate receiptDate;

    private String doNumber; // Nomor Surat Jalan dari Supplier

    private String notes;

    @Builder.Default
    private Boolean updateCostPrice = true; // Apakah harga pokok varian (costPrice) ikut diupdate ke harga beli ini

    @NotEmpty(message = "Penerimaan harus memiliki minimal 1 item produk")
    @Valid
    private List<GoodsReceiptItemRequest> items;
}

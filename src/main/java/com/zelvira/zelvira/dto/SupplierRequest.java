package com.zelvira.zelvira.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupplierRequest {
    private String code;

    @NotBlank(message = "Nama supplier wajib diisi")
    private String name;

    private String picName;
    private String phone;
    private String email;
    private String address;
    private String city;
    private String bankName;
    private String bankAccountNumber;
    private String bankAccountHolder;
    private String paymentTerm;
    private String notes;
    private Boolean isActive;
}

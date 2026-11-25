package com.zelvira.zelvira.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import io.swagger.v3.oas.annotations.media.Schema;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Data yang dibutuhkan untuk login menggunakan OTP via WhatsApp")
public class OtpLoginRequest {
    @NotBlank(message = "Phone number is required")
    @Schema(description = "Nomor WhatsApp (diawali dengan + atau 0)", example = "+628123456789")
    private String phoneNumber;

    @NotBlank(message = "OTP code is required")
    @Schema(description = "Kode OTP yang dikirimkan ke WhatsApp", example = "123456")
    private String code;
}

package com.zelvira.zelvira.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import io.swagger.v3.oas.annotations.media.Schema;

@Data
@Schema(description = "Data yang dibutuhkan untuk login menggunakan email dan password")
public class LoginRequest {
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    @Schema(description = "Alamat email terdaftar", example = "user@example.com")
    private String email;

    @NotBlank(message = "Password is required")
    @Schema(description = "Kata sandi pengguna", example = "secret123")
    private String password;
}

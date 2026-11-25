package com.zelvira.zelvira.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import io.swagger.v3.oas.annotations.media.Schema;

@Data
@Schema(description = "Data yang dibutuhkan untuk mendaftarkan akun baru")
public class RegisterRequest {
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    @Schema(description = "Alamat email baru", example = "budi@example.com")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 6, message = "Password must be at least 6 characters")
    @Schema(description = "Kata sandi akun (minimal 6 karakter)", example = "secret123")
    private String password;

    @NotBlank(message = "Full name is required")
    @Schema(description = "Nama lengkap pengguna", example = "Budi Santoso")
    private String fullName;
}

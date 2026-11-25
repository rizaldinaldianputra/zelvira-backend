package com.zelvira.zelvira.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Respons profil lengkap pengguna")
public class UserProfileResponse {

    @Schema(description = "ID unik pengguna", example = "1")
    private Long id;

    @Schema(description = "Alamat email pengguna", example = "budi@zelvira.com")
    private String email;

    @Schema(description = "Nama lengkap pengguna", example = "Budi Santoso")
    private String fullName;

    @Schema(description = "URL foto profil", example = "https://storage.zelvira.com/users/budi.jpg", nullable = true)
    private String profilePicture;

    @Schema(description = "Nomor telepon/WhatsApp", example = "+628123456789", nullable = true)
    private String phoneNumber;

    @Schema(description = "Saldo dompet (Rupiah)", example = "150000.0")
    private Double balance;

    @Schema(description = "Poin reward loyalitas", example = "250")
    private Long points;

    @Schema(description = "Daftar peran (hak akses) pengguna", example = "[\"ROLE_USER\"]")
    private List<String> roles;

    @Schema(description = "Sistem RBAC Role & Hak Akses Menu")
    private SystemRoleResponse systemRole;

}

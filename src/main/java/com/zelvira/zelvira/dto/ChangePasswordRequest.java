package com.zelvira.zelvira.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChangePasswordRequest {
    private String currentPassword;

    @NotBlank(message = "Password baru wajib diisi")
    @Size(min = 6, message = "Password baru minimal 6 karakter")
    private String newPassword;
}

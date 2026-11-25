package com.zelvira.zelvira.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Respons token hasil dari proses autentikasi (login/register)")
public class TokenResponse {

    @Schema(description = "JWT Bearer Token yang digunakan untuk Authorization header", example = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJyaXphbGRpQGdtYWlsLmNvbSIsImlhdCI6MTYyMjMwOTIwMH0.Xyz...")
    private String token;

}

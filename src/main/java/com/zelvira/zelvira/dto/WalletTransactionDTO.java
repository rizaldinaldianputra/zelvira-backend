package com.zelvira.zelvira.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WalletTransactionDTO {
    private Long id;
    private String type;
    private Double amount;
    private Long points;
    private String description;
    private String referenceId;
    private String status;
    private LocalDateTime createdAt;
}

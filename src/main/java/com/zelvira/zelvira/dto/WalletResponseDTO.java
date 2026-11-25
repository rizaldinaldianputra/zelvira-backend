package com.zelvira.zelvira.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WalletResponseDTO {
    private Double balance;
    private Long points;
    private List<WalletTransactionDTO> recentTransactions;
}

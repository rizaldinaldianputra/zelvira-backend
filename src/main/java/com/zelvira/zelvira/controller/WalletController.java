package com.zelvira.zelvira.controller;

import com.zelvira.zelvira.dto.ApiResponse;
import com.zelvira.zelvira.dto.WalletResponseDTO;
import com.zelvira.zelvira.security.CustomUserDetails;
import com.zelvira.zelvira.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/wallet")
public class WalletController {

    private final WalletService walletService;
    private final com.zelvira.zelvira.service.TopupService topupService;

    public WalletController(WalletService walletService, com.zelvira.zelvira.service.TopupService topupService) {
        this.walletService = walletService;
        this.topupService = topupService;
    }

    @GetMapping
    public ResponseEntity<WalletResponseDTO> getWallet(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(walletService.getWalletInfo(userDetails.getId()));
    }

    @PostMapping("/topup")
    public ResponseEntity<Map<String, Object>> topup(@AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody Map<String, Object> payload) {
        Double amount = Double.valueOf(payload.get("amount").toString());
        return ResponseEntity.ok(topupService.initiateTopup(userDetails.getId(), amount));
    }

    @PostMapping("/topup/{transactionId}/resume")
    public ResponseEntity<Map<String, Object>> resumeTopup(@AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long transactionId) {
        return ResponseEntity.ok(topupService.resumeTopup(transactionId, userDetails.getId()));
    }

    /**
     * Verify topup status directly from Midtrans API.
     * Called after payment window closes to update wallet balance without relying
     * on webhook.
     */
    @PostMapping("/topup/{transactionId}/verify")
    public ResponseEntity<Map<String, Object>> verifyTopup(@AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long transactionId) {
        Map<String, Object> result = topupService.verifyAndProcessTopup(transactionId, userDetails.getId());
        return ResponseEntity.ok(result);
    }
}
